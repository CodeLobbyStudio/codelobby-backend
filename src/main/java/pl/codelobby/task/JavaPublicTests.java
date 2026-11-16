package pl.codelobby.task;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record JavaPublicTests(
        @NotNull
        @Size(max = 240)
        @Pattern(regexp = "[A-Za-z_$][A-Za-z0-9_$]*(\\.[A-Za-z_$][A-Za-z0-9_$]*)*")
        String mainClass,
        @NotEmpty
        @Size(max = 50)
        List<@NotNull @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") String> testIds
) {
}
