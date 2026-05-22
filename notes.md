## ImageSummaryApp
* Parse the information into 3 sepereate parts including image, color#hex, and threshold 
* First check if all the values above fit the parameters or throws an error.
* Takes the target color and turns it into a 24bit integer/ binarizing.
* Then we take the color and use this equation and return the value 
  * sqrt((r1 - r2)^2 + (g1 - g2)^2 + (b1 - b2)^2)
  * following the equation we set each color into there own helper method and call the dif to shorten the equation. 
  * Use bit shifting by increments of 8 since its 24 bit RGB.
* Then we use the DistanceImageBinzarizer to make the image black and white 2d binarary array. Making each pixel 0 for black and 1 for white.
* Finally we use BinaryGroupFinder to see the location of all items/islands in the image and return the location. 

## DfsBinaryGroupFinder
* Searches through each pixel in the array and looks for a white pixel (represented as a 1)
* When a white pixel is found, searches for adjacent pixels that compose of the group
* Each group contains two values:
  * The size of the group, represented by the amount of connected pixels
  * The coordinate for the center of the group (eg. row=4 col=7 would display as [x: 7, y: 4])
* After collecting every group, orders them by size in descending order

## Function to test jar 
java -jar target/videoprocessor.jar sampleInput/ensantina.mp4 output-from-jar.csv 450907 25