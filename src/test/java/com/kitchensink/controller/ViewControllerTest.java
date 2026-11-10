package com.kitchensink.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.ui.ConcurrentModel;
import org.springframework.ui.Model;

/**
 * Unit tests for LoginController and MemberViewController.
 *
 * @author Deepak Bheemarasetty
 */
class ViewControllerTest {

  @Test
  void loginControllerViewName() {
    LoginController controller = new LoginController();
    assertThat(controller.showLogin()).isEqualTo("login");
  }

  @Test
  void memberViewControllerHomeAndMembers() {
    MongoTemplate mongoTemplate = mock(MongoTemplate.class);
    MemberViewController controller = new MemberViewController(mongoTemplate);
    Model model = new ConcurrentModel();

    Authentication auth =
        new TestingAuthenticationToken("admin", "pwd", List.of(() -> "member:view"));
    assertThat(controller.home(auth)).isEqualTo("redirect:/members");
    assertThat(controller.members(auth, model)).isEqualTo("all-members");
  }
}
