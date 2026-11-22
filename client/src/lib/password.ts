/** Password rule – the same as the backend (auth/PasswordRules.java): at least 8 characters, including a number. */
export const PASSWORD_HINT = "At least 8 characters, including a number";

export const passwordOk = (pw: string) => pw.length >= 8 && pw.length <= 72 && /\d/.test(pw);

/** Same check as the prototype forms. */
export const emailOk = (email: string) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(email.trim());
