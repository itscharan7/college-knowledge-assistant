package com.collegeassistant.admin;

import com.collegeassistant.admin.AdminService.Stats;
import com.collegeassistant.admin.AdminService.UnansweredDto;
import com.collegeassistant.feedback.FeedbackRow;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @GetMapping("/stats")
    public Stats stats() {
        return service.stats();
    }

    @GetMapping("/questions/unanswered")
    public List<UnansweredDto> unanswered(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.unanswered(page, size);
    }

    @GetMapping("/feedback")
    public List<FeedbackRow> feedback(@RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return service.feedback(page, size);
    }
}
