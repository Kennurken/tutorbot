package dev.kennurken.tutorbot.scheduling;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * External trigger for the same tick, used by a free cron pinger (cron-job.org) in production.
 * Two purposes: it keeps a free-tier host awake, and it is a safety net if the in-process
 * scheduler thread ever dies. Protected by a shared secret header; never by a query parameter
 * (query strings end up in access logs).
 */
@RestController
@RequestMapping("/internal/tick")
public class TickController {

    private final TickService tickService;
    private final SchedulerProperties props;

    public TickController(TickService tickService, SchedulerProperties props) {
        this.tickService = tickService;
        this.props = props;
    }

    @GetMapping
    public ResponseEntity<Map<String, Integer>> get(@RequestHeader(value = "X-Tick-Secret", required = false) String secret) {
        return run(secret);
    }

    @PostMapping
    public ResponseEntity<Map<String, Integer>> post(@RequestHeader(value = "X-Tick-Secret", required = false) String secret) {
        return run(secret);
    }

    private ResponseEntity<Map<String, Integer>> run(String secret) {
        if (!props.hasTickSecret() || !constantTimeEquals(props.tickSecret(), secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(tickService.runTick());
    }

    static boolean constantTimeEquals(String expected, String actual) {
        if (actual == null) {
            return false;
        }
        byte[] a = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = actual.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(a, b);
    }
}
