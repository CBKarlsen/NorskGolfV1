package fritids.norskgolf.service.clubs;

public record ClubRecord(
        String clubId,
        String name,
        double lat,
        double lon,
        String municipality,
        String county,
        Integer holes
) {}
