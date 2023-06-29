package com.mt.test_case.helper.args;

import com.mt.test_case.helper.AppConstant;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.springframework.http.HttpStatus;

public class ProjectIdArgs implements ArgumentsProvider {
    @Override
    public Stream<? extends Arguments> provideArguments(ExtensionContext extensionContext) {
        return Stream.of(
            Arguments.of("null", HttpStatus.BAD_REQUEST),
            Arguments.of("", HttpStatus.FORBIDDEN),
            Arguments.of("  ", HttpStatus.FORBIDDEN),
            Arguments.of(AppConstant.MT_ACCESS_PROJECT_ID, HttpStatus.FORBIDDEN)
        );
    }
}
