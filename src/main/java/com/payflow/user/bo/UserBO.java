package com.payflow.user.bo;

import com.payflow.user.domain.User;
import com.payflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UserBO {

    private final UserRepository userRepository;

    public List<User> getUserList() {
        return userRepository.findAll();
    }
}
