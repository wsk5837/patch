package com.gazellio.platform.controller;

import com.gazellio.platform.dto.ApiDtos.UserView;
import com.gazellio.platform.service.AccessControlService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/users") @RequiredArgsConstructor
public class UserController {
 private final AccessControlService access;
 @GetMapping("/assignees") public List<UserView> assignees(){
   return access.users().stream().filter(UserView::enabled).toList();
 }
}
