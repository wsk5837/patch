package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.UserView;
import com.gazellio.platform.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/users") @RequiredArgsConstructor
public class UserController {
 private final UserAccountRepository users;
 @GetMapping("/assignees") public List<UserView> assignees(){
   return users.findAll().stream().filter(u->u.isEnabled()).map(u->new UserView(u.getId(),u.getUsername(),u.getDisplayName(),u.getEmail(),u.getRole().name())).toList();
 }
}
