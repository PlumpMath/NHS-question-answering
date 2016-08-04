# NHS-question-answering
Simple question answering system based on data scraped from http://www.nhs.uk/


## Requirements
* jsoup (https://jsoup.org/)
* json-simple (https://code.google.com/archive/p/json-simple/)
* Stanford CoreNLP (http://www.java2s.com/Code/Jar/s/Downloadstanfordcorenlpjar.htm)


## Usage examples
You can find some usage examples [here](src/com/mikhail_dubov/nhs/QuestionAnswererTest.java).

To start the system as a service, run from this directory:

    $ python -m server.server

Then, you can make requests to this simple servers as follows:

    http://localhost:8080/answer?q=treatments+for+allergy

You will get the response in JSON format:

![Service example](https://cloud.githubusercontent.com/assets/1047242/17400998/50aa8c72-5a4b-11e6-94b7-b5ad9d8e49d0.png)


## JSON data scraped from NHS

The data from the NHS website has been scraped into a [JSON file](data/data.json) in a structured way:
* top-level keys are conditions;
* second-level keys are specific pages like symptoms/treatment/etc.;
* third-level keys define texts about particular aspects of these topics.

![NHS Data](https://cloud.githubusercontent.com/assets/1047242/17393659/63f39baa-5a25-11e6-8696-2cc22dca267b.png)
