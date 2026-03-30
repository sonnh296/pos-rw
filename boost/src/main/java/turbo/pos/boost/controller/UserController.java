package turbo.pos.boost.controller;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final CopyOnWriteArrayList<UserDto> users = new CopyOnWriteArrayList<>(List.of(
			new UserDto(UUID.randomUUID().toString(), "customer-001", "customer-001@demo.local", OffsetDateTime.now()),
			new UserDto(UUID.randomUUID().toString(), "customer-002", "customer-002@demo.local", OffsetDateTime.now()),
			new UserDto(UUID.randomUUID().toString(), "cashier-001", "cashier-001@demo.local", OffsetDateTime.now())
	));

	@GetMapping
	public List<UserDto> list() {
		return users;
	}

	@PostMapping
	public UserDto create(@RequestBody CreateUserRequest req) {
		String name = req == null || req.name() == null || req.name().isBlank() ? "user" : req.name().trim();
		String email = req == null ? null : req.email();
		UserDto user = new UserDto(UUID.randomUUID().toString(), name, email, OffsetDateTime.now());
		users.add(user);
		return user;
	}

	public record CreateUserRequest(String name, String email) {
	}

	public record UserDto(String id, String name, String email, OffsetDateTime createdAt) {
	}
}

