(ns wordle-solver.data )

(require '[clojure.string :as str])

;; LOAD 
(def answers-filename "wordle-answers.txt")
(def allowed-guesses-filename "wordle-allowed-guesses.txt")

(def dict-answers (str/split (slurp answers-filename) #"\n"))
(def dict-allowed-guesses (str/split (slurp allowed-guesses-filename) #"\n"))

(def test-answers-filename "test-wordle-answers.txt")
(def test-allowed-guesses-filename "test-wordle-allowed-guesses.txt")

(def test-dict-answers (str/split (slurp test-answers-filename) #"\n"))
(def test-dict-allowed-guesses (str/split (slurp test-allowed-guesses-filename) #"\n"))

