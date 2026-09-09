package com.homeopathy.clinic.auth;
public record LoginResponse(String accessToken,String tokenType,Long userId,String firstName,String lastName,String role){}
