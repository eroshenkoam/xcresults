package io.eroshenkoam.xcresults.carousel;

import java.io.Serializable;
import java.util.List;

public class Carousel implements Serializable {

    private final List<CarouselImage> images;

    public Carousel(final List<CarouselImage> images) {
        this.images = images;
    }

    public List<CarouselImage> getImages() {
        return images;
    }

}
