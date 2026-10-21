package com.seatsure;

import com.seatsure.booking.BookingDtos.BookingView;
import com.seatsure.booking.BookingDtos.HoldView;
import com.seatsure.booking.BookingRepository;
import com.seatsure.booking.BookingService;
import com.seatsure.booking.HoldExpiryJob;
import com.seatsure.booking.HoldService;
import com.seatsure.common.ConflictException;
import com.seatsure.common.GoneException;
import com.seatsure.event.EventDtos.CreateEventRequest;
import com.seatsure.event.EventDtos.EventSummary;
import com.seatsure.event.EventService;
import com.seatsure.event.Seat;
import com.seatsure.event.SeatRepository;
import com.seatsure.event.SeatStatus;
import com.seatsure.user.Role;
import com.seatsure.user.User;
import com.seatsure.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.mysql.MySQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against a real MySQL 8.4, because concurrency guarantees come from
 * InnoDB row locking — an in-memory database would prove nothing.
 *
 * Default: Testcontainers starts MySQL in Docker (this is what CI does).
 * No Docker? Point it at your own MySQL with environment variables:
 *   TEST_DB_URL=jdbc:mysql://localhost:3306/seatsure_test?serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
 *   TEST_DB_USERNAME=seatsure   TEST_DB_PASSWORD=seatsure
 * Use a SEPARATE database: the tests create their own users and events.
 */
@SpringBootTest(properties = "seatsure.seed-demo-event=false")
class BookingConcurrencyTest {

    static MySQLContainer mysql;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String externalUrl = System.getenv("TEST_DB_URL");
        if (externalUrl != null && !externalUrl.isBlank()) {
            registry.add("spring.datasource.url", () -> externalUrl);
            registry.add("spring.datasource.username", () -> envOr("TEST_DB_USERNAME", "seatsure"));
            registry.add("spring.datasource.password", () -> envOr("TEST_DB_PASSWORD", "seatsure"));
            return;
        }
        if (mysql == null) {
            mysql = new MySQLContainer("mysql:8.4");
            mysql.start(); // stopped automatically by Testcontainers when the JVM exits
        }
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @Autowired HoldService holdService;
    @Autowired BookingService bookingService;
    @Autowired HoldExpiryJob expiryJob;
    @Autowired EventService eventService;
    @Autowired SeatRepository seats;
    @Autowired BookingRepository bookings;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    Long eventId;
    List<Seat> grid;

    @BeforeEach
    void newEvent() {
        EventSummary event = eventService.create(new CreateEventRequest(
                "Test show " + UUID.randomUUID(), "Test hall", Instant.now().plus(Duration.ofDays(7)),
                null, 4, 10, new BigDecimal("300"), new BigDecimal("200"), new BigDecimal("100")));
        eventId = event.id();
        grid = seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(eventId);
    }

    @Test
    void fiftyUsersRaceForOneSeat_exactlyOneWins() throws Exception {
        Long seatId = grid.get(0).getId();
        List<Long> userIds = createUsers(50);

        AtomicInteger wins = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        runConcurrently(userIds.size(), i -> {
            try {
                holdService.hold(userIds.get(i), eventId, List.of(seatId));
                wins.incrementAndGet();
            } catch (ConflictException e) {
                conflicts.incrementAndGet();
            }
        });

        assertThat(wins.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(49);
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
    }

    @Test
    void overlappingMultiSeatHolds_neverShareASeat() throws Exception {
        // 20 users each grab 3 consecutive seats from a sliding window, so most requests overlap.
        List<Long> userIds = createUsers(20);
        runConcurrently(userIds.size(), i -> {
            List<Long> wanted = List.of(grid.get(i % 8).getId(), grid.get(i % 8 + 1).getId(), grid.get(i % 8 + 2).getId());
            try {
                holdService.hold(userIds.get(i), eventId, wanted);
            } catch (ConflictException | PessimisticLockingFailureException ignored) {
                // expected for losers (a deadlock victim is a loser too)
            }
        });

        // Every HELD seat belongs to exactly one hold, and every hold got all 3 seats or none.
        List<Seat> held = seats.findByEventIdOrderByRowLabelAscSeatNumberAsc(eventId).stream()
                .filter(s -> s.getStatus() == SeatStatus.HELD).toList();
        assertThat(held).isNotEmpty();
        held.stream().collect(Collectors.groupingBy(Seat::getHoldId))
                .values().forEach(group -> assertThat(group).hasSize(3));
    }

    @Test
    void confirmIsIdempotent_evenWhenRetriedInParallel() throws Exception {
        Long userId = createUsers(1).get(0);
        HoldView hold = holdService.hold(userId, eventId, List.of(grid.get(5).getId(), grid.get(6).getId()));
        String key = UUID.randomUUID().toString();

        Set<String> references = ConcurrentHashMap.newKeySet();
        runConcurrently(10, i -> {
            BookingView b = bookingService.confirm(userId, hold.holdId(), key);
            references.add(b.reference());
        });

        assertThat(references).hasSize(1);
        assertThat(bookings.findByHoldId(hold.holdId())).isPresent();
        assertThat(bookings.findByUserIdOrderByCreatedAtDesc(userId)).hasSize(1);
        assertThat(seats.findById(grid.get(5).getId()).orElseThrow().getStatus()).isEqualTo(SeatStatus.BOOKED);
    }

    @Test
    void expiredHold_isReleased_andCannotBeConfirmed() {
        Long userId = createUsers(1).get(0);
        Long seatId = grid.get(10).getId();
        HoldView hold = holdService.hold(userId, eventId, List.of(seatId));

        // Move the expiry 10 minutes into the past, relative to the stored value.
        // Done in SQL so it doesn't depend on the JVM or JDBC time zone (the app stores UTC).
        jdbc.update("UPDATE seat_holds SET expires_at = DATE_SUB(expires_at, INTERVAL 10 MINUTE) WHERE id = ?",
                hold.holdId());
        expiryJob.sweep();

        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.AVAILABLE);
        assertThatThrownBy(() -> bookingService.confirm(userId, hold.holdId(), "late-key"))
                .isInstanceOf(GoneException.class);

        // ...and someone else can now take the seat.
        Long other = createUsers(1).get(0);
        holdService.hold(other, eventId, List.of(seatId));
        assertThat(seats.findById(seatId).orElseThrow().getStatus()).isEqualTo(SeatStatus.HELD);
    }

    // ---------- helpers ----------

    private List<Long> createUsers(int n) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            ids.add(users.save(new User("u-" + UUID.randomUUID() + "@test.dev", "x", "Test User", Role.USER)).getId());
        }
        return ids;
    }

    /** Starts all tasks at the same instant to maximise contention. */
    private static void runConcurrently(int count, IntTask task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            int idx = i;
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                task.run(idx);
                return null;
            }));
        }
        ready.await();
        go.countDown();
        for (Future<?> f : futures) f.get(60, TimeUnit.SECONDS);
        pool.shutdown();
    }

    @FunctionalInterface
    interface IntTask {
        void run(int index) throws Exception;
    }
}
