; (use 'wordle-solver.core :reload)

(ns wordle-solver.core
  (:gen-class))

(require '[clojure.string :as str])
(require '[clojure.set])

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))


(def answers-filename "wordle-answers.txt")
(def allowed-guesses-filename "wordle-allowed-guesses.txt")

(def answers-dict (str/split (slurp answers-filename) #"\n"))
(def allowed-guesses-dict (str/split (slurp allowed-guesses-filename) #"\n"))

;; Note: doesn't check lengths
(defn match? [mask word]
  (every?
   #(or (= (first %) \_) (= (first %) (second %)))
   (map list mask word)))


(java.util.regex.Pattern/compile "\\d")

;; Note could also do guess answer candidate boolean, but this is probably
;; faster
(defn matcher-from-words [guess answer]
  (let [nomatches (apply str (clojure.set/difference (set guess) (set answer)))]
   nomatches
  ))


(def fn-d (partial filter #(str/includes? % "D")))
(def fn-reg1 #(filter (fn [w] (re-matches #"[^ER][^ER]A[^S]E" w)) %))

;;(defn match? 
;; (filter #(str/includes? % "D") (filter #(re-matches #"[^ER][^ER]A[^S]E" %) dict))

;; guess: ERASE
;; word:  SPATE
;;        0X101

;;   [^E]
;;   [^ER][^ER]
;;   [^ER][^ER] A 
;;   [^ER][^ER] A [^S]
;;   [^ER][^ER]A[^S]E
;;   includes S


(defn get-filtered-list [s-letters dict]
  (println "CALLING WITH" s-letters)    
  (if (= 1 (count s-letters))
    (filter #(str/includes? % (first s-letters)) dict)
    (filter #(str/includes? % (first s-letters)) (get-filtered-list (rest s-letters)))))
  
(def gfl-memo (memoize get-filtered-list))

;; PLAN:

;; w_guess 
;; For each word w_guess remaining:
;;; For each word w_answer in dict:
;;;; Compute response_mask(w_guess, w_answer)
;;;; Count non-matches in remaining .04 sec.
;;;; Return count, eliminated
;;; Take argmax of count
;;; remaining -> remaining minus eliminated

(defn regex-entry [s_answer s_eliminated l_guess l_answer]
  (cond (= l_guess l_answer) l_answer
        (s_answer l_guess) (str "[^" l_guess s_eliminated "]")
        :else (str "[^" s_eliminated "]")))


; TODO identity vs. memoize  
; TOOD Assumes dict as global - need to reset if changing at next move
(defn filter-dict [dict]
  ( #_memoize identity
   (fn [set-letters]
     (print ".")
     (if (= 1 (count set-letters))
       (filter #(str/includes? % (first set-letters)) dict)
       (filter #(str/includes? % (first set-letters))
               (filter-dict (rest set-letters)))))))

    
(defn count-remaining [dict w_guess w_answer]
  (let [s_guess (set w_guess)
        s_answer (set w_answer)
        s_eliminated (apply str (clojure.set/difference s_guess s_answer))
        s_overlap (clojure.set/intersection s_guess s_answer)
        regex-parts (map (partial regex-entry s_answer s_eliminated)
                   (map identity w_guess)
                   (map identity w_answer))
        regex (re-pattern (apply str regex-parts))
        dict-overlap (filter-dict (map str s_overlap))
        result (count (filter #(re-matches regex %) dict-overlap))
       _ (println w_answer result)
        ]
        result
       ))
  


;; TODO : Pre-generate all filtered sets of letters
(defn eliminators [w_guess dict]
  (zipmap dict (map (partial count-remaining dict w_guess) dict)))

; (defn 

;   (eliminators "STEAM")
;   (count-remaining dict "STEAM" "BEVOR")

  

;; eliminated-all(dict, remaining, word)
;;   = sum(eliminated_count(dict, word_i)) for all word_i in dict

;; because each word has equal probability, 
