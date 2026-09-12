package com.hankabakc.analyzepanel.auth.enums;

/* 
   Kullanıcı hesap durumlarını tanımlar.
   PENDING: Kayıt olmuş ancak onay bekleyen (Karantina).
   ACTIVE: Onaylanmış ve sisteme erişebilen.
   REJECTED: Onayı reddedilmiş.
*/
public enum UserStatus {
    PENDING,
    ACTIVE,
    REJECTED
}
