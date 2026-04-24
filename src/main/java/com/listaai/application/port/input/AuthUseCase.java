package com.listaai.application.port.input;

import com.listaai.application.port.input.command.*;

public interface AuthUseCase {
    AuthResult register(RegisterCommand command);
    AuthResult loginLocal(LoginCommand command);
    AuthResult loginGoogle(GoogleAuthCommand command);
    AuthResult refresh(RefreshCommand command);
    void logout(RefreshCommand command);
    void verifyEmail(VerifyEmailCommand command);
    void resendVerification(ResendVerificationCommand command);
}
