package ch.jp.shooting.model.auth;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;
import java.util.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@org.jspecify.annotations.NullMarked
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // ==================== AUTHENTICATION ====================
    @Column(unique = true, nullable = false)
    private String email;

    @Column(nullable = false)
    private String username; // Anzeige-Schreibweise (wie eingegeben)

    @Column(name = "username_lower", nullable = false, unique = true)
    private String usernameLower; // Kleinschreibung – für Login-Suche & Eindeutigkeit

    @Nullable
    @Column(name = "password_hash")
    private String passwordHash;

    // ==================== PERSONAL INFO ====================
    @Column(nullable = false)
    private String vorname; // first name

    @Column(nullable = false)
    private String nachname; // last name

    @Nullable
    @Column(name = "geburtsdatum")
    private LocalDate geburtsdatum; // date of birth

    @Nullable
    @Column(name = "geschlecht", length = 20)
    private String geschlecht; // MAENNLICH, WEIBLICH, DIVERS, UNBEKANNT

    // ==================== CONTACT INFO ====================
    @Nullable
    @Column(name = "telefonnummer", length = 20)
    private String telefonnummer; // phone number

    @Nullable
    @Column(name = "telefon_bestaetigt")
    private Boolean telefonBestaetigt = false; // phone verified flag

    @Nullable
    private String strasse; // street name

    @Nullable
    @Column(name = "hausnummer", length = 10)
    private String hausnummer; // house number

    @Nullable
    @Column(name = "plz", length = 10)
    private String plz; // postal code

    @Nullable
    private String stadt; // city

    @Nullable
    private String land; // country

    // ==================== PROFILE ====================
    @Nullable
    @Column(name = "profilbild_url")
    private String profilbildUrl; // profile picture URL

    @Nullable
    @Column(name = "biographie", length = 500)
    private String biographie; // bio/description

    @Nullable
    @Column(name = "sprache", length = 10)
    private String sprache; // preferred language: DE, EN, FR, IT

    // ==================== MEMBERSHIP & CREDENTIALS ====================
    @Nullable
    @Column(name = "mitgliedsnummer", unique = true)
    private String mitgliedsnummer; // membership number

    @Nullable
    @Column(name = "schiess_lizenz")
    private String schiessLizenz; // shooting license number

    @Nullable
    @Column(name = "schiess_lizenz_verfallsdatum")
    private LocalDate schiessLizenzVerfallsdatum; // shooting license expiry date

    // ==================== ACCOUNT STATUS ====================
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status = UserStatus.ACTIVE;

    @Nullable
    @Column(name = "email_bestaetigt")
    private Boolean emailBestaetigt = false; // email verified flag

    @Nullable
    @Column(name = "letzter_login")
    private Instant letzterLogin; // last login timestamp

    // ==================== AUDIT ====================
    @Column(nullable = false, name = "created_at")
    private Instant erstelltAm = Instant.now();

    @Column(nullable = false, name = "updated_at")
    private Instant aktualisiertAm = Instant.now();

    @Nullable
    @Column(name = "deleted_at")
    private Instant geloeschtAm; // soft delete timestamp

    // ==================== RELATIONSHIPS ====================
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<UserRoleEntity> userRoles = new HashSet<>();

    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.REMOVE)
    private Set<ScopedAccess> scopedAccess = new HashSet<>();

    // ==================== CONSTRUCTORS ====================
    public User() {}

    public User(String email, String vorname, String nachname) {
        this.email = email;
        this.vorname = vorname;
        this.nachname = nachname;
    }

    // ==================== GETTERS & SETTERS ====================

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getUsername() { return username; }

    /** Setzt den Benutzernamen und berechnet automatisch die Kleinschreibungs-Variante. */
    public void setUsername(String username) {
        this.username = username.trim();
        this.usernameLower = toUsernameLower(username);
    }

    public String getUsernameLower() { return usernameLower; }

    /** Normalisiert einen Benutzernamen für Eindeutigkeits-/Login-Vergleiche. */
    public static String toUsernameLower(String username) {
        return username.trim().toLowerCase(java.util.Locale.ROOT);
    }

    @Nullable
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(@Nullable String passwordHash) { this.passwordHash = passwordHash; }

    public String getVorname() { return vorname; }
    public void setVorname(String vorname) { this.vorname = vorname; }

    public String getNachname() { return nachname; }
    public void setNachname(String nachname) { this.nachname = nachname; }

    public String getFullName() {
        return vorname + " " + nachname;
    }

    @Nullable
    public LocalDate getGeburtsdatum() { return geburtsdatum; }
    public void setGeburtsdatum(@Nullable LocalDate geburtsdatum) { this.geburtsdatum = geburtsdatum; }

    @Nullable
    public String getGeschlecht() { return geschlecht; }
    public void setGeschlecht(@Nullable String geschlecht) { this.geschlecht = geschlecht; }

    @Nullable
    public String getTelefonnummer() { return telefonnummer; }
    public void setTelefonnummer(@Nullable String telefonnummer) { this.telefonnummer = telefonnummer; }

    @Nullable
    public Boolean getTelefonBestaetigt() { return telefonBestaetigt; }
    public void setTelefonBestaetigt(@Nullable Boolean telefonBestaetigt) { this.telefonBestaetigt = telefonBestaetigt; }

    @Nullable
    public String getStrasse() { return strasse; }
    public void setStrasse(@Nullable String strasse) { this.strasse = strasse; }

    @Nullable
    public String getHausnummer() { return hausnummer; }
    public void setHausnummer(@Nullable String hausnummer) { this.hausnummer = hausnummer; }

    @Nullable
    public String getPlz() { return plz; }
    public void setPlz(@Nullable String plz) { this.plz = plz; }

    @Nullable
    public String getStadt() { return stadt; }
    public void setStadt(@Nullable String stadt) { this.stadt = stadt; }

    @Nullable
    public String getLand() { return land; }
    public void setLand(@Nullable String land) { this.land = land; }

    @Nullable
    public String getFullAddress() {
        if (strasse == null || plz == null || stadt == null) {
            return null;
        }
        String addr = strasse;
        if (hausnummer != null && !hausnummer.isBlank()) {
            addr += " " + hausnummer;
        }
        addr += ", " + plz + " " + stadt;
        if (land != null && !land.isBlank()) {
            addr += ", " + land;
        }
        return addr;
    }

    @Nullable
    public String getProfilbildUrl() { return profilbildUrl; }
    public void setProfilbildUrl(@Nullable String profilbildUrl) { this.profilbildUrl = profilbildUrl; }

    @Nullable
    public String getBiographie() { return biographie; }
    public void setBiographie(@Nullable String biographie) { this.biographie = biographie; }

    @Nullable
    public String getSprache() { return sprache; }
    public void setSprache(@Nullable String sprache) { this.sprache = sprache; }

    @Nullable
    public String getMitgliedsnummer() { return mitgliedsnummer; }
    public void setMitgliedsnummer(@Nullable String mitgliedsnummer) { this.mitgliedsnummer = mitgliedsnummer; }

    @Nullable
    public String getSchiessLizenz() { return schiessLizenz; }
    public void setSchiessLizenz(@Nullable String schiessLizenz) { this.schiessLizenz = schiessLizenz; }

    @Nullable
    public LocalDate getSchiessLizenzVerfallsdatum() { return schiessLizenzVerfallsdatum; }
    public void setSchiessLizenzVerfallsdatum(@Nullable LocalDate schiessLizenzVerfallsdatum) {
        this.schiessLizenzVerfallsdatum = schiessLizenzVerfallsdatum;
    }

    public boolean isSchiessLizenzGueltig() {
        if (schiessLizenzVerfallsdatum == null) {
            return false;
        }
        return LocalDate.now().isBefore(schiessLizenzVerfallsdatum);
    }

    public UserStatus getStatus() { return status; }
    public void setStatus(UserStatus status) { this.status = status; }

    @Nullable
    public Boolean getEmailBestaetigt() { return emailBestaetigt; }
    public void setEmailBestaetigt(@Nullable Boolean emailBestaetigt) { this.emailBestaetigt = emailBestaetigt; }

    @Nullable
    public Instant getLetzterLogin() { return letzterLogin; }
    public void setLetzterLogin(@Nullable Instant letzterLogin) { this.letzterLogin = letzterLogin; }

    public Instant getErstelltAm() { return erstelltAm; }
    public Instant getAktualisiertAm() { return aktualisiertAm; }
    public void setAktualisiertAm(Instant aktualisiertAm) { this.aktualisiertAm = aktualisiertAm; }

    @Nullable
    public Instant getGeloeschtAm() { return geloeschtAm; }
    public void setGeloeschtAm(@Nullable Instant geloeschtAm) { this.geloeschtAm = geloeschtAm; }

    public Set<UserRoleEntity> getUserRoles() { return userRoles; }

    /** Abgeleitete Rollenmenge – nur lesend verwenden. */
    public Set<Role> getRoles() {
        return userRoles.stream()
            .map(UserRoleEntity::getRole)
            .collect(Collectors.toSet());
    }

    public Set<ScopedAccess> getScopedAccess() { return scopedAccess; }

    // ==================== ENUMS ====================
    public enum UserStatus {
        ACTIVE,
        INACTIVE,
        SUSPENDED,
        PENDING_APPROVAL
    }

    public enum Geschlecht {
        MAENNLICH("M"),
        WEIBLICH("W"),
        DIVERS("D"),
        UNBEKANNT("U");

        private final String code;
        Geschlecht(String code) { this.code = code; }
        public String getCode() { return code; }
    }

    public enum Sprache {
        DE("Deutsch"),
        EN("English"),
        FR("Français"),
        IT("Italiano");

        private final String label;
        Sprache(String label) { this.label = label; }
        public String getLabel() { return label; }
    }
}
