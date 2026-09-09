package com.homeopathy.clinic.user;
import jakarta.persistence.*; import java.time.OffsetDateTime;
@Entity @Table(name="users")
public class User {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false) private String firstName;
 @Column(nullable=false) private String lastName;
 @Column(nullable=false,unique=true) private String email;
 @Column(nullable=false) private String password;
 @Enumerated(EnumType.STRING) @Column(nullable=false) private Role role;
 @Column(nullable=false) private boolean active=true;
 private OffsetDateTime createdAt; private OffsetDateTime updatedAt;
 @PrePersist void create(){createdAt=OffsetDateTime.now();updatedAt=createdAt;}
 @PreUpdate void update(){updatedAt=OffsetDateTime.now();}
 public Long getId(){return id;} public String getFirstName(){return firstName;} public void setFirstName(String v){firstName=v;}
 public String getLastName(){return lastName;} public void setLastName(String v){lastName=v;}
 public String getEmail(){return email;} public void setEmail(String v){email=v;}
 public String getPassword(){return password;} public void setPassword(String v){password=v;}
 public Role getRole(){return role;} public void setRole(Role v){role=v;}
 public boolean isActive(){return active;} public void setActive(boolean v){active=v;}
}
