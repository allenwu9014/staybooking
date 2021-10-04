package com.laioffer.staybooking.service;

import com.laioffer.staybooking.exception.StayDeleteException;
import com.laioffer.staybooking.model.*;
import com.laioffer.staybooking.repository.LocationRepository;
import com.laioffer.staybooking.repository.StayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class StayService {

    private StayRepository stayRepository;
    private ImageStorageService imageStorageService;
    private LocationRepository locationRepository;
    private GeoEncodingService geoEncodingService;
    private ReservationRepository reservationRepository;



    @Autowired
    public StayService(StayRepository stayRepository, LocationRepository locationRepository,
                       GeoEncodingService geoEncodingService,
                       ReservationRepository reservationRepository,
                       ImageStorageService imageStorageService) {
        this.stayRepository = stayRepository;
        this.locationRepository = locationRepository;
        this.imageStorageService = imageStorageService;
        this.geoEncodingService = geoEncodingService;
        this.reservationRepository = reservationRepository;

    }
    public List<Stay> listByUser(String username) {
        return stayRepository.findByHost(new User.Builder().setUsername(username).build());
    }

    public Stay findByIdAndHost(Long stayId) {
        return stayRepository.findById(stayId).orElse(null);
    }

    public void add(Stay stay, MultipartFile[] images) {
        LocalDate date = LocalDate.now().plusDays(1);
        List<StayAvailability> availabilities = new ArrayList<>();
        for (int i = 0; i < 30; ++i) {
            availabilities.add(
                    new StayAvailability.Builder()
                    .setId(new StayAvailabilityKey(stay.getId(), date))
                    .setStay(stay)
                    .setState(StayAvailabilityState.AVAILABLE).build());
            date = date.plusDays(1);
        }
        stay.setAvailabilities(availabilities);

        // upload by parallel
        List<String> mediaLinks = Arrays.stream(images).parallel().map(image ->
                imageStorageService.save(image)).collect(Collectors.toList());
        List<StayImage> stayImages = new ArrayList<>();
        for (String mediaLink : mediaLinks) {
            stayImages.add(new StayImage(mediaLink, stay));
        }
        stay.setImages(stayImages);


        stayRepository.save(stay);
        Location location = geoEncodingService.getLatLng(stay.getId(), stay.getAddress());
        locationRepository.save(location);

    }

    public void delete(Long stayId) throws StayDeleteException {
        List<Reservation> reservations = reservationRepository.findByStayAndCheckoutDateAfter(new Stay.Builder().setId(stayId).build(), LocalDate.now());
        if (reservations != null && reservations.size() > 0) {
            throw new StayDeleteException("Cannot delete stay with active reservation");
        }

        stayRepository.deleteById(stayId);
    }
}
