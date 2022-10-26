(ns wordle-solver.harness)

(require '[wordle-solver.eval :as eval])
(require '[clojure.pprint])

(defn generate-random-word [l-answer-dict] 
    (rand-nth l-answer-dict))


(def initial-game-state 
  {
    :round 0
    :rounds-finished '()
    :found-words '()
    :guesses '()
  })

(defn update-game-state [game-state w-guess l-response-masks]
   (let [new-round (inc (:round game-state))
         num-finished (count (filter #(= '(2 2 2 2 2) %) l-response-masks))
         new-found-words (concat (:found-words game-state) (repeat num-finished w-guess))
         new-rounds-finished (concat (:rounds-finished game-state)
                                     (repeat num-finished new-round))
         new-guesses (concat (:guesses game-state) (list w-guess))] 
     { 
       :round new-round
       :rounds-finished new-rounds-finished
       :found-words new-found-words
       :guesses new-guesses 
     }))


(defn log-results [answers-set game-state s-outputfilename]
  (spit s-outputfilename (apply str (interpose "," answers-set)) :append true)
  (spit s-outputfilename ";" :append true)
  (spit s-outputfilename (apply str (interpose "," (:rounds-finished game-state)))
        :append true)
  (spit s-outputfilename ";" :append true)
  (spit s-outputfilename (apply str (interpose "," (:guesses game-state))) :append true)
  (spit s-outputfilename \newline :append true)
  (clojure.pprint/pprint "DONE LOGGING"))


(defn -drop-indices [col indices]
  (let [s-indices (set indices)]
    (filter identity (map-indexed #(if ((complement s-indices) %1) %2) col))))

(defn -find-indices [col target]
  (keep-indexed #(when (= %2 target) %1) col))

;; EXAMPLE for running one 1-dle trial:
;; (run-one-trial
;;      1
;;	r-firstmove
;;	'(answer)
;;	data/dict-allowed-guesses
;;	eval/select-best-guess-global-min
;;      "output.txt")
(defn run-one-trial [board-size
                     r-firstmove
                     answers-set-oneboard
                     l-allowed-guesses
                     f-heuristic
                     s-outputfilename]
    (loop [game-state initial-game-state
           remaining-answers-set answers-set-oneboard
           l-results (repeat board-size r-firstmove)
           w-guess (f-heuristic l-results (:found-words game-state))]
            (let [
                  l-response-masks (map (partial eval/guess-and-answer-to-mask w-guess)
                                        remaining-answers-set)

                  new-game-state (update-game-state game-state w-guess l-response-masks)

                  _ (println "== NEW GAME STATE ==")
                  _ (clojure.pprint/pprint new-game-state)

                  ;; TODO - avoid sloppy use of 22222 
                  correct-indices (-find-indices l-response-masks '(2 2 2 2 2))
                  [l-new-results l-new-answer-lists] (eval/play-moves
                                                      l-allowed-guesses
                                                      w-guess l-response-masks l-results)
                  l-results (-drop-indices l-new-results correct-indices)
                  l-answer-lists (-drop-indices l-new-answer-lists correct-indices)
                  remaining-answers-set (-drop-indices remaining-answers-set correct-indices)]

              (if (zero? (count remaining-answers-set))
                ;; termination
                (log-results answers-set-oneboard new-game-state s-outputfilename)

                ;; else continue
                (let [w-next-guess (f-heuristic l-results (:found-words new-game-state))]
                  (recur new-game-state remaining-answers-set l-results w-next-guess))))))

;; Example - run over a list of boards 
;;      (run-many-trials
;;      4
;;	r-firstmove
;;	'(["spore", "deity", "manga", "snoop"]
;;           ["radio", "creak", "touch", "burst"])
;;	data/dict-allowed-guesses
;;	eval/select-best-guess-global-min
;;        "output.txt")
(defn run-many-trials [board-size
                     r-firstmove
                     l-answers-set
                     l-allowed-guesses
                     f-heuristic
                       s-outputfilename]
  (map (fn [answer]
         (println "Answers set")
         (clojure.pprint/pprint answer)
         (run-one-trial
          board-size
          r-firstmove
           answer
           l-allowed-guesses
           f-heuristic
           s-outputfilename))
       l-answers-set))


;; Example - run 10 random 2-dle trials
;;     (harness/run-random-trials
;;       10
;;       2
;;       r-firstmove
;;       data/dict-answers
;;       data/dict-allowed-guesses
;;       eval/select-best-guess-global-min
;;       "output.txt")
(defn run-random-trials  [n
                     board-size
                     r-firstmove
                     l-answers-dict     
                     l-allowed-guesses
                     f-heuristic
                     s-outputfilename]
  
  (dotimes [_ n]
    (let [answer (repeatedly board-size #(generate-random-word l-answers-dict))
          _ (println "Answers set")
          _ (clojure.pprint/pprint answer)]

         (run-one-trial
          board-size
          r-firstmove
           answer
           l-allowed-guesses
           f-heuristic
           s-outputfilename))))
