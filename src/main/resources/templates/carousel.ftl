<#-- @ftlvariable name="carousel" type="io.eroshenkoam.xcresults.carousel.Carousel" -->
<html lang="en">
<head>
    <link rel="stylesheet" href="https://maxcdn.bootstrapcdn.com/bootstrap/3.4.1/css/bootstrap.min.css">
    <script src="https://ajax.googleapis.com/ajax/libs/jquery/3.6.4/jquery.min.js"></script>
    <script src="https://maxcdn.bootstrapcdn.com/bootstrap/3.4.1/js/bootstrap.min.js"></script>
    <title>Carousel</title>
</head>
<body>
<div id="myCarousel" class="carousel slide" data-ride="carousel" style="height: 100%">
    <ol class="carousel-indicators">
        <#list 0..carousel.images?size-1 as i>
            <@carouselIndicator i />
        </#list>
    </ol>
    <div class="carousel-inner" role="listbox">
        <#list 0..carousel.images?size-1 as i>
            <@carouselItem carousel.images[i].name carousel.images[i].base64 i />
        </#list>
    </div>
    <!-- Left and right controls -->
    <a class="left carousel-control" href="#myCarousel" role="button" data-slide="prev">
        <span class="glyphicon glyphicon-chevron-left" aria-hidden="true"></span>
        <span class="sr-only">Previous</span>
    </a>
    <a class="right carousel-control" href="#myCarousel" role="button" data-slide="next">
        <span class="glyphicon glyphicon-chevron-right" aria-hidden="true"></span>
        <span class="sr-only">Next</span>
    </a>
</div>
</body>
</html>

<#macro carouselIndicator index>
    <li data-target="#myCarousel" data-slide-to="${index}" class="<#if index == 0>active</#if>"></li>
</#macro>
<#macro carouselItem name base64 index>
    <div class="item <#if index == 0>active</#if>">
        <img alt="${name}" src="${base64}">
    </div>
</#macro>
