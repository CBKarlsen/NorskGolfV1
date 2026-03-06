package fritids.norskgolf.dto;

public record CourseDto(
        Long id,
        String name,
        Double latitude,
        Double longitude,
        String externalId,
        boolean played
) {}