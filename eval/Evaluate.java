import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/**
 * Black-box evaluation against a RUNNING app. Run with a JDK (no Maven needed):
 *   java eval/Evaluate.java http://localhost:8080 user@example.com password eval/questions.tsv
 * Use a STUDENT account to test restricted-document behaviour. Output: per-question result + summary.
 * Measures: refusal correctness, citation match (title contains / page equals). It cannot measure Hit@K
 * (the API only returns the cited sources, not all retrieved chunks).
 * UNVERIFIED: written without running against the app.
 */
public class Evaluate {
    static final Pattern CSRF = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"|value=\"([^\"]+)\"[^>]*name=\"_csrf\"");
    static final Pattern META = Pattern.compile("<meta name=\"_csrf\" content=\"([^\"]+)\"");

    public static void main(String[] a) throws Exception {
        if (a.length < 4) { System.err.println("usage: java Evaluate.java <baseUrl> <email> <password> <questions.tsv>"); System.exit(2); }
        String base = a[0].replaceAll("/$", "");
        CookieManager cm = new CookieManager();
        HttpClient http = HttpClient.newBuilder().cookieHandler(cm).followRedirects(HttpClient.Redirect.NEVER).build();

        // login
        String loginPage = http.send(HttpRequest.newBuilder(URI.create(base + "/login")).build(), HttpResponse.BodyHandlers.ofString()).body();
        Matcher m = CSRF.matcher(loginPage);
        if (!m.find()) throw new IllegalStateException("CSRF token not found on login page");
        String token = m.group(1) != null ? m.group(1) : m.group(2);
        String form = "username=" + enc(a[1]) + "&password=" + enc(a[2]) + "&_csrf=" + enc(token);
        HttpResponse<String> lr = http.send(HttpRequest.newBuilder(URI.create(base + "/login"))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build(),
                HttpResponse.BodyHandlers.ofString());
        String loc = lr.headers().firstValue("Location").orElse("");
        if (lr.statusCode() / 100 != 3 || loc.contains("error")) throw new IllegalStateException("Login failed: " + lr.statusCode() + " " + loc);

        // csrf token for API calls comes from a page's meta tag
        String chat = http.send(HttpRequest.newBuilder(URI.create(base + "/chat")).build(), HttpResponse.BodyHandlers.ofString()).body();
        Matcher mm = META.matcher(chat);
        if (!mm.find()) throw new IllegalStateException("CSRF meta tag not found on /chat");
        String apiToken = mm.group(1);

        List<String[]> rows = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(a[3]), StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#")) continue;
            String[] c = line.split("\t", -1);
            rows.add(Arrays.copyOf(c, 7));
        }

        Map<String, String> convByQuestion = new HashMap<>();
        int total = 0, refusalOk = 0, refusalTotal = 0, answerOk = 0, answerTotal = 0, citeOk = 0, citeTotal = 0, errors = 0;
        for (String[] r : rows) {
            String id = r[0], type = r[1], q = r[2], expect = r[3], title = nz(r[4]), page = nz(r[5]), follow = nz(r[6]);
            String conv = follow.isEmpty() ? null : convByQuestion.get(follow);
            if (conv == null) conv = json(http, base + "/api/chat/conversations", apiToken, "{}", "\"id\"\\s*:\\s*\"([^\"]+)\"");
            HttpResponse<String> resp = http.send(HttpRequest.newBuilder(URI.create(base + "/api/chat/conversations/" + conv + "/ask"))
                    .header("Content-Type", "application/json").header("X-CSRF-TOKEN", apiToken)
                    .POST(HttpRequest.BodyPublishers.ofString("{\"question\":\"" + esc(q) + "\"}")).build(), HttpResponse.BodyHandlers.ofString());
            convByQuestion.put(id, conv);
            total++;
            if (resp.statusCode() != 200) { errors++; System.out.printf("%-4s %-13s ERROR HTTP %d%n", id, type, resp.statusCode()); continue; }
            String body = resp.body();
            boolean answerable = body.matches("(?s).*\"answerable\"\\s*:\\s*true.*");
            boolean ok;
            String note = "";
            if (expect.equals("REFUSE")) { refusalTotal++; ok = !answerable; if (ok) refusalOk++; }
            else {
                answerTotal++; ok = answerable; if (ok) answerOk++;
                if (answerable && (!title.isEmpty() || !page.isEmpty())) {
                    citeTotal++;
                    boolean hit = citationMatches(body, title, page);
                    if (hit) citeOk++; else note = " (citation mismatch)";
                }
            }
            System.out.printf("%-4s %-13s %s%s%n", id, type, ok ? "PASS" : "FAIL", note);
        }
        System.out.println("\n== Summary (" + total + " questions, " + errors + " HTTP errors) ==");
        System.out.printf("Correct refusals:   %d/%d%n", refusalOk, refusalTotal);
        System.out.printf("Answered answerable:%d/%d%n", answerOk, answerTotal);
        System.out.printf("Citation matches:   %d/%d%n", citeOk, citeTotal);
    }

    static boolean citationMatches(String body, String title, String page) {
        Matcher m = Pattern.compile("\"documentTitle\"\\s*:\\s*\"([^\"]*)\"[^}]*?\"page\"\\s*:\\s*(\\d+)").matcher(body);
        while (m.find()) {
            boolean t = title.isEmpty() || m.group(1).toLowerCase().contains(title.toLowerCase());
            boolean p = page.isEmpty() || m.group(2).equals(page);
            if (t && p) return true;
        }
        return false;
    }

    static String json(HttpClient http, String url, String token, String body, String regex) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/json")
                .header("X-CSRF-TOKEN", token).POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        Matcher m = Pattern.compile(regex).matcher(r.body());
        if (!m.find()) throw new IllegalStateException("Unexpected response from " + url + ": " + r.statusCode() + " " + r.body());
        return m.group(1);
    }
    static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8); }
    static String esc(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\""); }
    static String nz(String s) { return s == null ? "" : s.trim(); }
}
