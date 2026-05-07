package turbo.pos.boost.service;

import org.springframework.stereotype.Service;
import turbo.pos.boost.dto.CreateUserRequest;
import turbo.pos.boost.dto.UserDto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class UserService {

    private final CopyOnWriteArrayList<UserDto> users = new CopyOnWriteArrayList<>(List.of(
            new UserDto(UUID.randomUUID().toString(), "customer-001", "customer-001@demo.local", OffsetDateTime.now()),
            new UserDto(UUID.randomUUID().toString(), "customer-002", "customer-002@demo.local", OffsetDateTime.now()),
            new UserDto(UUID.randomUUID().toString(), "cashier-001", "cashier-001@demo.local", OffsetDateTime.now())
    ));

    public List<UserDto> getAllUsers() {
        return users;
    }

    public UserDto createUser(CreateUserRequest req) {
        String name = req == null || req.name() == null || req.name().isBlank() ? "user" : req.name().trim();
        String email = req == null ? null : req.email();
        UserDto user = new UserDto(UUID.randomUUID().toString(), name, email, OffsetDateTime.now());
        users.add(user);
        return user;
    }

    public UserDto updateUser(String id, CreateUserRequest req) {
        for (int i = 0; i < users.size(); i++) {
            UserDto u = users.get(i);
            if (u.id().equals(id)) {
                String name = req == null || req.name() == null || req.name().isBlank() ? u.name() : req.name().trim();
                String email = req == null || req.email() == null ? u.email() : req.email();
                UserDto updated = new UserDto(u.id(), name, email, u.createdAt());
                users.set(i, updated);
                return updated;
            }
        }
        return null;
    }

    public boolean deleteUser(String id) {
        return users.removeIf(u -> u.id().equals(id));
    }
}
