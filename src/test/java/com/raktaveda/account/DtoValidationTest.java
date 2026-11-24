package com.raktaveda.account;

import static org.assertj.core.api.Assertions.assertThat;

import com.raktaveda.account.AccountDtos.DeleteConfirmDto;
import com.raktaveda.notification.NotificationDtos.DeviceTokenDto;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DtoValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void deleteConfirmNeedsTheExactWordDelete() {
        UUID id = UUID.randomUUID();
        assertThat(validator.validate(new DeleteConfirmDto(id, "123456", "DELETE"))).isEmpty();
        assertThat(validator.validate(new DeleteConfirmDto(id, "123456", "delete"))).isNotEmpty();
        assertThat(validator.validate(new DeleteConfirmDto(id, "123456", "yes"))).isNotEmpty();
        assertThat(validator.validate(new DeleteConfirmDto(id, "123456", null))).isNotEmpty();
    }

    @Test
    void deleteConfirmNeedsADigitsOnlyCode() {
        UUID id = UUID.randomUUID();
        assertThat(validator.validate(new DeleteConfirmDto(id, "12ab56", "DELETE"))).isNotEmpty();
        assertThat(validator.validate(new DeleteConfirmDto(null, "123456", "DELETE"))).isNotEmpty();
    }

    @Test
    void deviceTokenMustLookLikeAPushToken() {
        assertThat(validator.validate(new DeviceTokenDto("dGVzdC10b2tlbi0xMjM0NTY3ODkw:APA91b"))).isEmpty();
        assertThat(validator.validate(new DeviceTokenDto("short"))).isNotEmpty();
        assertThat(validator.validate(new DeviceTokenDto("has spaces in it and is long enough"))).isNotEmpty();
        assertThat(validator.validate(new DeviceTokenDto(null))).isNotEmpty();
    }
}
