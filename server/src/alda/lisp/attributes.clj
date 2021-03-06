(ns alda.lisp.attributes
  (:require [alda.lisp.model.key]))
(in-ns 'alda.lisp)

(def ^:dynamic *initial-attr-values* {:current-offset (AbsoluteOffset. 0)
                                      :last-offset (AbsoluteOffset. 0)
                                      :current-marker :start})

(defn- percentage [x]
  {:pre [(<= 0 x 100)]}
  (constantly (/ x 100.0)))

(defn- unbound-percentage [x]
  {:pre [(<= 0 x)]}
  (constantly (/ x 100.0)))

;; Validation that the input is an integer value
(defn- pos-num [x]
  {:pre [(and (number? x)
              (pos? x))]}
  (constantly x))

(defattribute tempo
  "Current tempo. Used to calculate the duration of notes."
  :initial-val 120
  :transform pos-num)

(defattribute duration
  "Default note duration in beats."
  :initial-val 1
  :fn-name set-duration
  ;; :aliases [:duration]
  :transform (fn [val]
               {:pre [(or
                       (map? val)
                       (and (number? val) (pos? val)))]}

               (constantly (if (map? val)
                             (:value val)
                             val))))

(defattribute octave
  "Current octave. Used to calculate the pitch of notes."
  :initial-val 4
  :transform (fn [val]
               {:pre [(or (integer? val)
                          (contains? #{:down :up} val))]}
               (case val
                :down dec
                :up inc
                (constantly val))))

(defattribute quantization
  "The percentage of a note that is heard.
   Used to put a little space between notes.

   e.g. with a quantization value of 90%, a note that would otherwise last
   500 ms will be quantized to last 450 ms. The resulting note event will
   have a duration of 450 ms, and the next event will be set to occur in 500 ms."
  :aliases [:quant :quantize]
  :initial-val 0.9
  :transform unbound-percentage)

(defattribute volume
  "Current volume. For MIDI purposes, the velocity of individual notes."
  :aliases [:vol]
  :initial-val 1.0
  :transform percentage)

(defattribute track-volume
  "More general volume for the track as a whole. Although this can be changed
   just as often as volume, to do so is not idiomatic. For MIDI purposes, this
   corresponds to the volume of a channel."
  :aliases [:track-vol]
  :initial-val (/ 100.0 127.0)
  :transform percentage)

(defattribute panning
  "Current panning."
  :aliases [:pan]
  :initial-val 0.5
  :transform percentage)

(defn- validate-str-key-sig
  "Validates the current key-sig. Checks for:

  1. No duplicate letters, ie: a- a+
  2. No letters out of range a-g

  If all tests pass, return true"
  [key-sig]
  ;; Get a version of key-sig with only characters
  (let [clean-str (apply str (filter #(Character/isLetter %) key-sig))]
    (and (not (re-find #"[^a-g]" clean-str))
         (= (count (distinct clean-str)) (count clean-str)))))

(defn- parse-key-signature
  "Transforms a key signature into a letter->accidentals map.

   If the key signature is already provided as a letter->accidentals map
   (e.g. {:f [:sharp] :c [:sharp] :g [:sharp]}), then it passes through this
   function unchanged.

   If the key signature is provided as a string, e.g. 'f+ c+ g+', then it is
   converted to a letter->accidentals map."
  [key-sig]
  {:pre [(or (not (string? key-sig))
             (validate-str-key-sig key-sig))]}

  (constantly
    (cond
      (map? key-sig)
      key-sig

      (string? key-sig)
      (into {}
        (map (fn [[_ _ letter accidentals]]
               [(keyword letter)
                (map {\- :flat \+ :sharp \= :natural} accidentals)])
             (re-seq #"(([a-g])([+-=]*))" key-sig)))

      (sequential? key-sig)
      (let [[scale-type & more]    (reverse key-sig)
            [letter & accidentals] (reverse more)]
        (get-key-signature scale-type letter accidentals)))))

(defattribute key-signature
   "The key in which the current instrument is playing."
   :aliases [:key-sig]
   :initial-val {}
   :transform parse-key-signature)
