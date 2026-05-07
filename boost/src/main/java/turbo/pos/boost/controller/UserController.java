package turbo.pos.boost.controller;

import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import turbo.pos.boost.dto.CreateUserRequest;
import turbo.pos.boost.dto.UserDto;
import turbo.pos.boost.service.UserService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;

	@GetMapping
	public List<UserDto> list() {
		return userService.getAllUsers();
	}

	@PostMapping
	public UserDto create(@RequestBody CreateUserRequest req) {
		return userService.createUser(req);
	}

	@PutMapping("/{id}")
	public ResponseEntity<UserDto> update(@PathVariable String id, @RequestBody CreateUserRequest req) {
		UserDto updated = userService.updateUser(id, req);
		return updated != null ? ResponseEntity.ok(updated) : ResponseEntity.notFound().build();
	}

	@DeleteMapping("/{id}")
	public ResponseEntity<Void> delete(@PathVariable String id) {
		boolean removed = userService.deleteUser(id);
		return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
	}
}
