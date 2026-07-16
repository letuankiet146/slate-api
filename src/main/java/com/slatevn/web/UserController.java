package com.slatevn.web;

import com.slatevn.dto.CreateUserRequest;
import com.slatevn.dto.UpdateUserRequest;
import com.slatevn.dto.UserDto;
import com.slatevn.security.SecurityUtils;
import com.slatevn.service.UserService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserDto> list() {
        return userService.list(SecurityUtils.currentUser().getId());
    }

    @GetMapping("/{id}")
    public UserDto get(@PathVariable UUID id) {
        return userService.get(SecurityUtils.currentUser().getId(), id);
    }

    @PostMapping
    public UserDto create(@Valid @RequestBody CreateUserRequest request) {
        return userService.create(SecurityUtils.currentUser().getId(), request);
    }

    @PatchMapping("/{id}")
    public UserDto update(@PathVariable UUID id, @RequestBody UpdateUserRequest request) {
        return userService.update(SecurityUtils.currentUser().getId(), id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        userService.delete(SecurityUtils.currentUser().getId(), id);
    }
}
