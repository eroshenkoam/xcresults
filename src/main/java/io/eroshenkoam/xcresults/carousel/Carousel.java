package io.eroshenkoam.xcresults.carousel;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class Carousel implements Serializable {

    private final List<CarouselImage> images;

    public Carousel(final List<CarouselImage> images) {
        this.images = Optional.ofNullable(images).orElseGet(ArrayList::new);
    }

    public List<CarouselImage> getImages() {
        return images;
    }

}
