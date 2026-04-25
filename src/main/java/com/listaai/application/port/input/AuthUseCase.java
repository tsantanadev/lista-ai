package com.listaai.application.port.input;

import com.listaai.application.port.input.command.*;

public interface AuthUseCase {
    /**
     * @return non-null AuthResult when verification is disabled; null when enabled
     *         (user must verify email before tokens are issued).
     */
    AuthResult register(RegisterCommand command);
    AuthResult loginLocal(LoginCommand command);
    AuthResult loginGoogle(GoogleAuthCommand command);
    AuthResult refresh(RefreshCommand command);
    void logout(RefreshCommand command);
    void verifyEmail(VerifyEmailCommand command);
    void resendVerification(ResendVerificationCommand command);
}
