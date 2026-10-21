package com.seatsure.common;

import com.seatsure.config.SeatSureProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Tells the UI which optional features are switched on (e.g. hide the AI button when AI is off). */
@RestController
public class PublicConfigController {

    private final SeatSureProperties props;

    public PublicConfigController(SeatSureProperties props) {
        this.props = props;
    }

    @GetMapping("/api/config")
    public Map<String, Object> config() {
        return Map.of("aiEnabled", props.ai().enabled(), "holdSeconds", props.hold().ttlSeconds());
    }
}
