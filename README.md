clojure-tollbooth
=================

A highly parallel (1000+ threads) tollbooth simulation, inspired by [Rich Hickey's ant colony simulation](http://juliangamble.com/blog/2011/12/28/clojure-gui-demo-of-ants/).

Cars, each represented by a single thread, move down their lane towards the tollbooth. Upon reaching the tollbooth, they wait a random amount of time, before moving past. Cars queue behind stopped cars, and move left and right to avoid long lines.

Despite the high level of concurrency, over 1000 threads, there are no concurrency conflicts thanks mostly to Clojures's great built-in [concurrency library](http://clojure.org/concurrent_programming).


How to Run
----------

To run this simulation, you will need [Leiningen](https://github.com/technomancy/leiningen) installed.

To fire up the simulation, just clone this repo and type `lein run`.


Configuration
-------------

There are a few variables that define how the simulation runs. Unfortunately, at this time, they must be specified in the code, rather than at the command line.

To specify these variables, edit `src/tollbooth/core.clj`. They are located near the top of the code.

Relevant variables:
  - `lanes`: how many "lanes" of cars will be simulated
  - `lane-length`: how many car-lengths long each lane is
  - `scale`: how many pixels (square) each car takes up in the GUI
  - `lower-wait` and `upper-wait`: how long (in ms) cars wait at the tollbooth
