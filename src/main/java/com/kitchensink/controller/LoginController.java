package com.kitchensink.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Serves the sign-in choice page. */
@Controller
public class LoginController {
  @GetMapping("/login")
  public String showLogin() {
    return "login";
  }
}
