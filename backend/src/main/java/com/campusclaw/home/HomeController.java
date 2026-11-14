package com.campusclaw.home;

import com.campusclaw.auth.CurrentUserService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/home")
public class HomeController {
    private final CurrentUserService users;
    private final HomeService home;

    public HomeController(CurrentUserService users, HomeService home) {
        this.users = users;
        this.home = home;
    }

    @GetMapping("/teacher")
    public Map<String, Object> teacher() { return home.teacher(users.requireTeacher()); }

    @GetMapping("/student")
    public Map<String, Object> student() { return home.student(users.requireUser()); }
}
