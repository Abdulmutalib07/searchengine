package searchengine.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public class ApiResponse {
    private final boolean result;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String error;

    public static ApiResponse success() {
        return new ApiResponse(true);
    }

    public static ApiResponse error(String errorMessage) {
        return new ApiResponse(false, errorMessage);
    }
}
