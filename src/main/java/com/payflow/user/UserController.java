package com.payflow.user;

import com.payflow.user.bo.UserBO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserBO userBO;

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userBO.getUserList());
        return "users"; // templates/users.html
    }
}
