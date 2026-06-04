package ch.jp.shooting.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
@org.jspecify.annotations.NullMarked
public class UserDTO {
    private UUID id;
    private String email;
    private String vorname;
    private String nachname;
    private String fullName;

    @Nullable
    private LocalDate geburtsdatum;

    @Nullable
    private String geschlecht;

    @Nullable
    private String telefonnummer;

    @Nullable
    private String strasse;

    @Nullable
    private String hausnummer;

    @Nullable
    private String plz;

    @Nullable
    private String stadt;

    @Nullable
    private String land;

    @Nullable
    private String profilbildUrl;

    @Nullable
    private String biographie;

    @Nullable
    private String sprache;

    @Nullable
    private String mitgliedsnummer;

    @Nullable
    private String schiessLizenz;

    @Nullable
    private LocalDate schiessLizenzVerfallsdatum;

    private String status;
    private Instant erstelltAm;
    private Instant aktualisiertAm;

    @Nullable
    private UUID assignedRangeId;

    // ==================== CONSTRUCTORS ====================
    public UserDTO() {}

    // ==================== GETTERS & SETTERS ====================
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getVorname() { return vorname; }
    public void setVorname(String vorname) { this.vorname = vorname; }

    public String getNachname() { return nachname; }
    public void setNachname(String nachname) { this.nachname = nachname; }

    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }

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

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Instant getErstelltAm() { return erstelltAm; }
    public void setErstelltAm(Instant erstelltAm) { this.erstelltAm = erstelltAm; }

    public Instant getAktualisiertAm() { return aktualisiertAm; }
    public void setAktualisiertAm(Instant aktualisiertAm) { this.aktualisiertAm = aktualisiertAm; }

    @Nullable
    public UUID getAssignedRangeId() { return assignedRangeId; }
    public void setAssignedRangeId(@Nullable UUID assignedRangeId) { this.assignedRangeId = assignedRangeId; }
}
