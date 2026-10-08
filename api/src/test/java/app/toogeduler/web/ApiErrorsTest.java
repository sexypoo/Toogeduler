package app.toogeduler.web;

import app.toogeduler.domain.Event;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 클라이언트 입력 문제는 500 이 아니라 4xx 와 읽을 수 있는 메시지로 돌아와야 한다. */
class ApiErrorsTest {
    @RestController static class Probe{
        @GetMapping("/missing") String missing(){throw new NoSuchElementException();}
        @GetMapping("/zone") String zone(@RequestParam String tz){return ZoneId.of(tz).getId();}
        @GetMapping("/range") String range(@RequestParam OffsetDateTime from){return from.toString();}
        @PostMapping("/move") String move(@RequestBody java.util.Map<String,String> body){return OffsetDateTime.parse(body.get("startAt")).toString();}
        @GetMapping("/boom") String boom(){throw new IllegalStateException("bug");}
    }
    private final MockMvc mvc=MockMvcBuilders.standaloneSetup(new Probe()).setControllerAdvice(new ApiErrors()).build();

    @Test void missingEntityIs404()throws Exception{mvc.perform(get("/missing")).andExpect(status().isNotFound()).andExpect(jsonPath("$.message").exists());}
    @Test void invalidTimeZoneIs400()throws Exception{mvc.perform(get("/zone").param("tz","Mars/Olympus")).andExpect(status().isBadRequest());}
    @Test void malformedDateParamIs400()throws Exception{mvc.perform(get("/range").param("from","yesterday")).andExpect(status().isBadRequest());}
    @Test void missingParamIs400()throws Exception{mvc.perform(get("/range")).andExpect(status().isBadRequest());}
    @Test void malformedDateInBodyIs400()throws Exception{mvc.perform(post("/move").contentType(MediaType.APPLICATION_JSON).content("{\"startAt\":\"nope\"}")).andExpect(status().isBadRequest());}
    @Test void brokenJsonIs400()throws Exception{mvc.perform(post("/move").contentType(MediaType.APPLICATION_JSON).content("{")).andExpect(status().isBadRequest());}
    @Test void unexpectedErrorIs500WithoutDetails()throws Exception{mvc.perform(get("/boom")).andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").value("잠시 후 다시 시도해주세요."));}

    @Test void eventInputRejectsValuesTheDatabaseCannotStore(){
        Validator validator=Validation.buildDefaultValidatorFactory().getValidator();
        OffsetDateTime start=OffsetDateTime.parse("2026-10-08T10:00:00+09:00");
        EventController.EventInput tooLong=new EventController.EventInput("제목","x".repeat(256),"",start,start.plusHours(1),false,Set.of(Event.Visibility.PRIVATE),null,"",null,"#168CF2",Set.of());
        EventController.EventInput badRule=new EventController.EventInput("제목","","",start,start.plusHours(1),false,Set.of(Event.Visibility.PRIVATE),null,"FREQ=HOURLY",null,"#168CF2",Set.of());
        EventController.EventInput badColor=new EventController.EventInput("제목","","",start,start.plusHours(1),false,Set.of(Event.Visibility.PRIVATE),null,"",null,"red; drop table",Set.of());
        EventController.EventInput ok=new EventController.EventInput("제목","메모","장소",start,start.plusHours(1),false,Set.of(Event.Visibility.PRIVATE),null,"FREQ=WEEKLY",30,"#19b7b1",Set.of());
        assertFalse(validator.validate(tooLong).isEmpty());
        assertFalse(validator.validate(badRule).isEmpty());
        assertFalse(validator.validate(badColor).isEmpty());
        assertTrue(validator.validate(ok).isEmpty());
    }
}
