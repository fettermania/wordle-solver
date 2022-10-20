# wordle-solver

This is a REPL-based assistant to solving "easy mode" Wordle, as well as a small simulation harness for testing various solving heuristics.

It's also usable in "n-dle", where n is any positive interger (1 = Wordle, 4 = Quordle, 8 = Octordle, etc.).

The paper written to justify the use of its main heuristic (Shannon's Information Entropy) across n-dle is here: http://fettermania.com/ndle.pdf, as well as in the repository.

## Installation

Download from http://github.com/fettermania/wordle-solver.

cd to that directory, then 

    $ lein repl

## Usage

Usage is mostly shown by example in the "notes" file.

### Usage: Loading the initial state.

If needed, edit dictionary of allowed guesses at wordle-allowed-guesses.txt.
If needed, edit dictionary of possible answers at wordle-answers.txt

Load these dictionaries

	$ (def l-answers dict-answers)
	$ (def l-allowed-guesses dict-allowed-guesses)

Then, since our solver is determinstic, the evaluation of the first move can be cached immediately before 
doing any simulations or making our first guess on a blank board.

	$ (def r-firstmove   (evaluate-all-moves l-answers l-allowed-guesses)) 

This produces an list of moves of increasing entropy of the form:

     $ ["soare" ;; the guess
     $ 	   {:entropy 5.287849713481362, ;; entropy over the result possibilities
     $	   :matches {
     $	   	    (2 0 2 2 0) ["scarf" "scary" "shard" "shark" ;; ...
     $	   	    (2 0 2 0 0) ["scald" "scalp" "scaly" "scamp" ;; ...
     $		    }}]
     $	["roate" {:entropy 5.288196144042106 ;; ...
     	  ]
     $    ;; ...
     $	   

### Usage: Solving a puzzle in n-dle

Detailed in the "notes" file, we generate our options before our first move:

	$ (def l-results (repeat 4 r-firstmove)) ;; set up our best guess for N (= 4) boards
	$ (def game-state harness-initial-game-state) ;; used primarily for simulations
	$ (def f-heuristic harness-select-best-guess-summed) ;; or select harness-select-best-guess-min (see paper)
	$ (pprint (take 10 (-sum-entropies (map just-words-and-entropy l-results)))) ;; a glimpse
	$ (def w-guess (f-heuristic l-results (:found-words game-state))) ;; make first guess

Then, for each move on, say, quordle.com, enter in 0 (black), 1 (yellow) or (2) from the boards
in l-response-masks, and run the remainder as copy-paste.

	$ (def l-response-masks '(
	$   (2 2 2 2 2) 
	$   (2 2 2 2 2) 
	$   (0 1 1 1 0) 
	$   (2 2 2 2 2) 
	$   ))
	$ 
	$ (def game-state (harness-update-game-state game-state w-guess l-response-masks))
	$ 
	$ (println "New game state ")
	$ (pprint game-state)
	$ 
	$ ;; Make Move manually
	$ (def move-results
	$   (play-moves l-allowed-guesses w-guess l-response-masks l-results))
	$ 
	$ (def l-results (first move-results))
	$ (def l-answer-lists (second move-results))
	$ 
	$ (pprint
	$   (map list
	$      (map first l-results)
	$      l-answer-lists))
	$ 
	$ (pprint (take 10 (-sum-entropies (map just-words-and-entropy l-results))))
	$ 
	$ (def w-guess (f-heuristic l-results (:found-words game-state)))
	$ 
	$ (println "STOP. Play move " w-guess)


### Usage: Running simulations

Example: Run over all in in dictionary for Wordle (1-dle) using global min heuristic.

	$ (map (fn [answer] 
	$   (let [answers-set (repeat 1 answer)
	$         _ (println "Answers set")
	$         _ (pprint answers-set)]
	$         (harness-run-one-trial (repeat 1 r-firstmove) answers-set l-allowed-guesses harness-select-best-guess-global-min)))
	$       l-answers)


Example: Do 100 random trials for 2-dle using global min heuristic.

	$ (dotimes [n 100]
	$   (let [answers-set (repeatedly 2 #(harness-generate-random-word l-answers))
	$         _ (println "Answers set")
	$         _ (pprint answers-set)]
	$         (harness-run-one-trial (repeat 2 r-firstmove) answers-set l-allowed-guesses harness-select-best-guess-global-min)))

Note: The harness always logs output to "./output.txt"

### Usage: (Optional) Using pattern-matching to limit the dictionary.

Sometimes you maty see friends' solutions *before* you solve, which can give the enterprising Wordle shark extra information.

It's not uncommon to see solutions grids (0 = BLACK, 1 = YELLOW, 2 = GREEN) like 

01210
10202
02222
02222
02222
22222

Assuming they haven't repeated guesses, this means the answer has to be among a set of at least four words which share the last four letters.  You can use this to your advantage by pre-eliminating those ansers from the diction ary with:

	$ (def l-answers dict-answers) ;; or nest earlier uses of the below function.
	$ (count l-answers) ;; yields 2309
	$ (def l-answers (select-similar-block '(0 2 2 2 2) 4 l-answers))
	$ (count l-answers) ;; yields 2087

You can combine several word lists (sequneces of word strings) with

	$ (def l-answers (intersect-blocks l-answers1 l-answers2))

e.g. 
	$ (intersect-blocks '("agave" "grape" "sugar") '("grape" "green" "agave") ;; yields '("agave" "grape")

## Enchancements (TODOs)

### Notes for presenation

- lemel
- -100
- what to do with dead words?
- tiebreaking
- pmap
- Clojure 1.8?
- Emacs
  - Note: Can't upgrade cider?
  - C-c M-j cider-jack-in
  - C-x C-e execute last
  - C-c C-k compile
  - Many commands don't work
  - C-M M-n M-n namespace set
  
### Fragility to errors

If something's entered incorrectly, you will end up in the "I don't know, lemel" case quickly.

### Multi-file clojure setup

core.clj is the only file and namespace.  This could be cleaner.  Also, not hardcoding "output.txt".  Prerequisite for a Seajure presentation.

Could also relearn Clojure+Emacs for a Seajure presentation.

### Clearer tie-breaking

If entropies are equal, should we pick the alphabetically first word to be sure of consistency?  
Or pick randomly to represent the probability distribution accurately?
Right now, this relies on the Clojure (JVM?) sort hashing to break ties.

Note: Alex Healy's implementation does break what I consider ties first by preferring words in the answer key.

### Enhancement: Build hard mode in

The difference between hard mode and easy mode is exactly what should go in "l-allowed-guesses".
In easy mode, l-allowed-guesses never changes - it's always precisely the loaded allowed guess dictionary.
In hard mode, l-allowed-guesses needs to respect the yellow and green (but can ignore the black) elements of the previous response, and therefore, the intersection of all previous responses.  

It is possible to set l-allowed-guesses to l-answers (or, more specifically, the subset that hasn't been eliminated).  It hasn't been implemented.

