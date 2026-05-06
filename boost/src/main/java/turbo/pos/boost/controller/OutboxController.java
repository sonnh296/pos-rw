package turbo.pos.boost.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import turbo.pos.boost.service.OutboxService;

import java.util.Map;

@RestController
@RequestMapping("/api/outbox")
@RequiredArgsConstructor
public class OutboxController {

	private final OutboxService outboxService;

	@GetMapping("/stats")
	public Map<String, Object> stats() {
		return outboxService.getStats();
	}
}
