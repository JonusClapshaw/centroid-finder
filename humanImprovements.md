## refactoring code
* What improvements can you make to the design/architecture of your code?
    * Cut long arguments to a shorter more readable version.
* How can you split up large methods or classes into smaller components?
    * Taking re-used methods and cutting them into smaller classes.
    * Adding more public classes to our code.
* Are there unused files/methods that can be removed?
    * ImageSummaryApp
    * TBD
* Where would additional Java interfaces be appropriate?
    * TBD
* How can you make things simpler, more-usable, and easier to maintain?
    * Public classes
    * Constant variables
    * Documentation on methods and comments
* Other refactoring improvements?
    * TBD
## adding tests
* What portions of your code are untested / only lightly tested?
    * Most of our code does not have a lot of edge-case tests.
* Where would be the highest priority places to add new tests?
    * More edge-case testing for CsvWriter.java.
* Other testing improvements?
    * TBD
## improving error handling
* What parts of your code are brittle?
    * Little to no error handing for threshold and color picker.
* Where could you better be using exceptions?
    * More IllegalArgumentExceptions.
* Where can you better add input validation to check invalid input?
    * Better input validation in VideoProcessorApp.java.
* How can you better be resolving/logging/surfacing errors? Hint: almost any place you're using "throws Exception" or "catch(Exception e)" should likely be improved to specify the specific types of exceptions that might be thrown or caught.
    * Error messages can be better written for debugging purposes.
* Other error handling improvements?
    * TBD
## writing documentation
* What portions of your code are missing Javadoc/JSdoc for the methods/classes?
    * Writing documentation for mostly all our test
    * Writing documentation and expection outcome for classes
* What documentation could be made clearer or improved?
    * Most of the ending codes implemented in stage 2
    * TBD
* Are there sections of dead code that are commented out?
    * No
* Where would be the most important places to add documentation to make your code easier to read?
    * On top of our classes and functions
* Other documentation improvements?
    * TBD
## improving performance (optional)
* What parts of your code / tests run particularly slowly?
    * The 7 minute long video
    * More testing for check time
* What speed improvements would most make running / maintaining your code better?
    * Parsing argument to take less characters
    * Checking loops and recursive methods
* Other performance improvements?
    * TBD
## hardening security (optional)
* What packages / images are out of date / have security issues?
* Where could you have better input validation in your code to prevent malicious use?
* Other security improvements?
## bug fixes (optional)
* What bugs do you know exist?
* What parts of the code do you think might be causing them?
* Other bug fix improvements?
## other
* Any other improvements in general you could make?