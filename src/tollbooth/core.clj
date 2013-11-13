(ns tollbooth.core)

; Joe Einertson
; CSci 4651 Spring 2012 - Problem Set 8
; http://cda.morris.umn.edu/~elenam/4651spring2012/psets/ps8.html


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; car sim ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;   Copyright (c) Rich Hickey. All rights reserved.
;   The use and distribution terms for this software are covered by the
;   Common Public License 1.0 (http://opensource.org/licenses/cpl.php)
;   which can be found in the file CPL.TXT at the root of this distribution.
;   By using this software in any fashion, you are agreeing to be bound by
;   the terms of this license.
;   You must not remove this notice, or any other, from this software.


;number of toll lanes
(def lanes 50)
;length of each lane
(def lane-length 45)
;pixels per world cell
(def scale 10)
;toll wait lower bound (in ms)
(def lower-wait 500)
;toll wait upper bound (in ms)
(def upper-wait 1000)


(def dir-delta {:south [0 1]
                :southwest [-1 1]
                :southeast [1 1]})


(def animation-sleep-ms 100)
(def car-sleep-ms 80)

(def running true)

(defstruct cell :booth :car)

;world is a 2d vector of refs to cells
(def world 
     (apply vector 
            (map (fn [_] 
                   (apply vector (map (fn [_] (ref (struct cell false false))) 
                                      (range lane-length)))) 
                 (range lanes))))

(defn place [[x y]]
  (-> world (nth x) (nth y)))

(defstruct car :dir)

(defn create-car 
  "create an car in the specified lane, returning an car agent on the location"
  [x]
    (sync nil
      (let [loc [x 0]
            p (place loc)
            a (struct car :south)]
        (alter p assoc :car a)
        (agent loc))))

(defn rand-in-bounds
  "returns a random integer between upper and lower bounds (inclusive)"
  [lower upper]
  (+ (rand-int (inc (- upper lower))) lower))

(defn despawn-car
  "waits a random amount of time and despawns a car"
  [a loc]
  (let [wait-time (rand-in-bounds lower-wait upper-wait)
        p (place loc)]
    (. Thread (sleep wait-time))
    (sync nil
          (alter p assoc :car false))))


(defn setup-booths
  "places toll booths"
  []
  (sync nil
        (doall
          (for [i (range lanes)]
            (let [p (place [i (dec lane-length)])] 
              (alter p assoc :booth true))))))


(defn delta-loc 
  "returns the location one step in the given dir. Note the world is a torus"
  [[x y] dir]
    (let [[dx dy] (dir-delta dir)]
      [(mod (+ x dx) lanes) (mod (+ y dy) lane-length)]))

;;;;;;;;;;;;;;;;;;;;;;;;;; car agent functions ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;a car agent tracks the location of an car, and controls the behavior of 
;the car at that location

(defn move 
  "moves the car a certain direction. Must be called in a
  transaction that has verified the way is clear"
  [loc dir]
     (let [oldp (place loc)
           car (:car @oldp)
           newloc (delta-loc loc dir)
           p (place newloc)]
         ;move the car
       (alter p assoc :car car)
       (alter oldp assoc :car false)
     newloc))

(defn behave 
  "the main function for the car agent"
  [loc]
  (let [p (place loc)
        car (:car @p)
        ahead (place (delta-loc loc :south))
        ahead-left (place (delta-loc loc :southeast))
        ahead-right (place (delta-loc loc :southwest))]
    (. Thread (sleep car-sleep-ms))
    (dosync
     (when (and running (not (:booth @ahead)))
       (send-off *agent* #'behave))
        (cond 
          (:booth @ahead)
            (send-off *agent* #'despawn-car loc)
          (not (:car @ahead))
            (move loc :south)
          (not (:car @ahead-left))
            (move loc :southeast)
          (not (:car @ahead-right))
            (move loc :southwest)
          :else ; (:car @ahead)
            loc))))

(defn spawn-loop
  "sleeps a random amount of time, then spawns a car"
  [x]
  (while running
    (let [wait-time (rand-in-bounds lower-wait upper-wait)]
      (. Thread (sleep wait-time))
      (send-off (create-car x) #'behave))))

(defn start-spawners
  "creates one car spawner per lane and spins it off into its own
  thread, which then begins execution"
  []
  (doall
    (for [i (range lanes)]
      (.start (Thread. #(spawn-loop i))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; UI ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(import 
 '(java.awt Color Graphics Dimension)
 '(java.awt.image BufferedImage)
 '(javax.swing JPanel JFrame))


(defn fill-cell [#^Graphics g x y c]
  (doto g
    (.setColor c)
    (.fillRect (* x scale) (* y scale) scale scale)))

(defn render-car [car #^Graphics g x y]
  (let [black (. (new Color 0 0 0 255) (getRGB))
        gray (. (new Color 100 100 100 255) (getRGB))
        red (. (new Color 255 0 0 255) (getRGB))
        [hx hy tx ty] [2 4 2 0]]
    (doto g
      (.setColor (new Color 0 0 0 255))
      (.fillRect (* scale (+ 0.25 x)) (* scale (+ 0.125 y)) 
                 (* 0.5 scale) (* 0.75 scale)))))

(defn render-place [g p x y]
  (when (:booth p)
    (fill-cell g x y (new Color 255 0 0 255)))
  (when (:car p)
    (render-car (:car p) g x y)))

(defn render [g]
  (let [v (dosync (apply vector (for [x (range lanes) y (range lane-length)] 
                                   @(place [x y]))))
        img (new BufferedImage (* scale lanes) (* scale lane-length) 
                 (. BufferedImage TYPE_INT_ARGB))
        bg (. img (getGraphics))]
    (doto bg
      (.setColor (. Color white))
      (.fillRect 0 0 (. img (getWidth)) (. img (getHeight))))
    (dorun 
      (for [x (range lanes) y (range lane-length)]
        (let [zzz (v (+ (* x lane-length) y))]
          (render-place bg zzz x y)
          )))
    (doto bg
      (.setColor (. Color blue)))
    (. g (drawImage img 0 0 nil))
    (. bg (dispose))))

(def panel (doto (proxy [JPanel] []
                        (paint [g] (render g)))
             (.setPreferredSize (new Dimension 
                                     (* scale lanes) 
                                     (* scale lane-length)))))

(def frame (doto (new JFrame "Toll Booth Simulation") (.add panel) .pack .show))

(def animator (agent nil))

(defn animation [x]
  (when running
    (send-off *agent* #'animation))
  (. panel (repaint))
  (. Thread (sleep animation-sleep-ms))
  nil)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;; use ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn -main [& args]
  (setup-booths)
  (send-off animator animation)
  (start-spawners))
