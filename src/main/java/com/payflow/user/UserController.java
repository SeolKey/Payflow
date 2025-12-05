package com.payflow.user;

import com.payflow.user.bo.UserBO;
import com.payflow.user.domain.User;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequiredArgsConstructor
public class UserController {

    private final UserBO userBO;

    @GetMapping("/signup")
    public String signupForm() {
        return "signup";
    }

    @PostMapping("/signup")
    public String signup(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            RedirectAttributes redirectAttributes
    ) {
        try {
            userBO.registerUser(name, email, password);
            redirectAttributes.addFlashAttribute("success", "회원가입이 완료되었습니다. 로그인해주세요.");
            return "redirect:/login";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/signup";
        }
    }

    @GetMapping("/login")
    public String loginForm(HttpSession session) {
        // 이미 로그인한 경우 홈으로 리다이렉트
        if (session.getAttribute("userId") != null) {
            return "redirect:/";
        }
        return "login";
    }

    @PostMapping("/login")
    public String login(
            @RequestParam String email,
            @RequestParam String password,
            HttpSession session,
            RedirectAttributes redirectAttributes
    ) {
        try {
            User user = userBO.login(email, password);
            
            // 세션에 사용자 정보 저장
            session.setAttribute("userId", user.getId());
            session.setAttribute("userEmail", user.getEmail());
            session.setAttribute("userName", user.getName());
            
            redirectAttributes.addFlashAttribute("success", "로그인되었습니다.");
            return "redirect:/";
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/login";
        }
    }

    @PostMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    @GetMapping("/users")
    public String users(Model model) {
        model.addAttribute("users", userBO.getUserList());
        return "users";
    }
}
