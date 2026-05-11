package ch.jp.shooting.mapper;

import ch.jp.shooting.dto.UserDTO;
import ch.jp.shooting.model.auth.User;
import org.springframework.stereotype.Component;

@Component
@org.jspecify.annotations.NullMarked
public class UserMapper {

    public UserDTO toDto(User user) {
        if (user == null) return null;

        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setEmail(user.getEmail());
        dto.setVorname(user.getVorname());
        dto.setNachname(user.getNachname());
        dto.setFullName(user.getFullName());
        dto.setGeburtsdatum(user.getGeburtsdatum());
        dto.setGeschlecht(user.getGeschlecht());
        dto.setTelefonnummer(user.getTelefonnummer());
        dto.setStrasse(user.getStrasse());
        dto.setHausnummer(user.getHausnummer());
        dto.setPlz(user.getPlz());
        dto.setStadt(user.getStadt());
        dto.setLand(user.getLand());
        dto.setProfilbildUrl(user.getProfilbildUrl());
        dto.setBiographie(user.getBiographie());
        dto.setSprache(user.getSprache());
        dto.setMitgliedsnummer(user.getMitgliedsnummer());
        dto.setSchiessLizenz(user.getSchiessLizenz());
        dto.setSchiessLizenzVerfallsdatum(user.getSchiessLizenzVerfallsdatum());
        dto.setStatus(user.getStatus().toString());
        dto.setErstelltAm(user.getErstelltAm());
        dto.setAktualisiertAm(user.getAktualisiertAm());

        return dto;
    }

    public User toEntity(UserDTO dto) {
        if (dto == null) return null;

        User user = new User();
        user.setId(dto.getId());
        user.setEmail(dto.getEmail());
        user.setVorname(dto.getVorname());
        user.setNachname(dto.getNachname());
        user.setGeburtsdatum(dto.getGeburtsdatum());
        user.setGeschlecht(dto.getGeschlecht());
        user.setTelefonnummer(dto.getTelefonnummer());
        user.setStrasse(dto.getStrasse());
        user.setHausnummer(dto.getHausnummer());
        user.setPlz(dto.getPlz());
        user.setStadt(dto.getStadt());
        user.setLand(dto.getLand());
        user.setProfilbildUrl(dto.getProfilbildUrl());
        user.setBiographie(dto.getBiographie());
        user.setSprache(dto.getSprache());
        user.setMitgliedsnummer(dto.getMitgliedsnummer());
        user.setSchiessLizenz(dto.getSchiessLizenz());
        user.setSchiessLizenzVerfallsdatum(dto.getSchiessLizenzVerfallsdatum());

        return user;
    }
}
