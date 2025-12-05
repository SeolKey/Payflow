package com.payflow.user.bo;

import com.payflow.user.domain.User;
import com.payflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserBO {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> getUserList() {
        return userRepository.findAll();
    }

    /**
     * 회원가입 처리
     */
    public User registerUser(String name, String email, String rawPassword) {
        // 이메일 중복 검사
        if (validateEmail(email)) {
            throw new RuntimeException("이미 사용 중인 이메일입니다.");
        }

        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // 사용자 생성 및 저장
        User user = new User(name, email, encodedPassword);
        return userRepository.save(user);
    }

    /**
     * 로그인 검증
     */
    public User login(String email, String rawPassword) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        
        if (userOpt.isEmpty()) {
            throw new RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        User user = userOpt.get();
        
        // 비밀번호 검증
        if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new RuntimeException("이메일 또는 비밀번호가 올바르지 않습니다.");
        }

        return user;
    }

    /**
     * 이메일로 사용자 조회
     */
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    /**
     * 이메일 중복 검사
     */
    public boolean validateEmail(String email) {
        return userRepository.findByEmail(email).isPresent();
    }
}
