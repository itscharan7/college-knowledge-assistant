package com.collegeassistant.web;

import com.collegeassistant.common.ApiException;
import com.collegeassistant.config.AppProperties;
import com.collegeassistant.document.Audience;
import com.collegeassistant.security.AppUserDetails;
import com.collegeassistant.user.Role;
import com.collegeassistant.user.UserDtos.RegisterForm;
import com.collegeassistant.user.UserService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Serves the Thymeleaf pages. Data is loaded by the page's JavaScript from the /api endpoints. */
@Controller
public class PageController {

    private final UserService userService;
    private final Validator validator;
    private final AppProperties props;

    public PageController(UserService userService, Validator validator, AppProperties props) {
        this.userService = userService;
        this.validator = validator;
        this.props = props;
    }

    @GetMapping("/")
    public String home(@AuthenticationPrincipal AppUserDetails me) {
        return me != null && me.getRole() == Role.ADMIN ? "redirect:/admin" : "redirect:/chat";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    /** Public registration always creates a STUDENT; there is no role parameter anywhere in this flow. */
    @PostMapping("/register")
    public String register(@RequestParam String name, @RequestParam String email, @RequestParam String password,
                           @RequestParam String confirmPassword, Model model) {
        RegisterForm form = new RegisterForm(name, email, password, confirmPassword);
        List<String> errors = new ArrayList<>();
        for (ConstraintViolation<RegisterForm> v : validator.validate(form)) {
            errors.add(v.getPropertyPath() + " " + v.getMessage());
        }
        if (errors.isEmpty()) {
            try {
                userService.registerStudent(form);
                return "redirect:/login?registered";
            } catch (ApiException e) {
                errors.add(e.getMessage());
            }
        }
        model.addAttribute("errors", errors);
        model.addAttribute("name", name);
        model.addAttribute("email", email);
        return "register";
    }

    @GetMapping("/chat")
    public String chat(@AuthenticationPrincipal AppUserDetails me, Model model) {
        model.addAttribute("userName", me.getName());
        model.addAttribute("roleName", me.getRole().name());
        return "chat";
    }

    @GetMapping("/admin")
    public String dashboard() {
        return "admin/dashboard";
    }

    @GetMapping("/admin/documents")
    public String documents(Model model) {
        model.addAttribute("categories", props.getCategories());
        model.addAttribute("audiences", Audience.values());
        return "admin/documents";
    }

    @GetMapping("/admin/users")
    public String users() {
        return "admin/users";
    }
}
