# wordle-solver

This is a REPL-based assistant to solving "easy mode" Wordle,
as well as a small simulation harness for testing various solving heuristics.

It's also usable in "n-dle", where n is any positive interger
(1 = Wordle, 4 = Quordle, 8 = Octordle, etc.).

The paper written to justify the use of its main heuristic (Shannon's Information Entropy)
across n-dle is here: http://fettermania.com/ndle.pdf, as well as in the repository.


## Usage

### Loading dictionaries, creating the initial state

If needed, edit dictionary of allowed guesses in ./wordle-allowed-guesses.txt, and 
   dictionary of possible answers in ./wordle-answers.txt

cd to that directory, then 

    $ lein repl

Since our solver is determinstic, the evaluation of the first move can be done immediately before 
doing any simulations or making our first guess on a blank board.

Since this state applies to the starting position of any puzzle, and since
generation of this large map takes about a minute on an 8-core MacBook M1 c. 2021,
we tend to keep this structure around.

     (def r-firstmove (evaluate-first-move))

This produces an list of moves of increasing entropy of the form:

     ["soare" ;; the guess
     	   {:entropy 5.287849713481362, ;; entropy over the result possibilities
     	   :matches {

	       ;; Note: 0 = eval/BLACK, 1 = eval/YELLOW, 2 = eval/GREEN
     	       (2 0 2 2 0) ["scarf" "scary" "shard" "shark" ;; ...
     	       (2 0 2 0 0) ["scald" "scalp" "scaly" "scamp" ;; ...
           }}]
      ["roate" {:entropy 5.288196144042106 ;; ...
     	  ]
        ;; ...
     	   

### Solving a live ndle game:

To use this in a loop to solve a live Quordle game:

     ;; Example usage:
     (ns wordle-solver.core)
     (require '[wordle-solver.harness :as harness])
     (require '[wordle-solver.data :as data])
     (require '[wordle-solver.eval :as eval])
     
     ; Using namespace wordle-solver.core:
     
     (def r-firstmove (evaluate-first-move))
     
     (def ndle-state  (initialize-ndle-state
          4 ;; number of simultaneous boards.  Here, 4 for "Quordle"
          eval/select-best-guess-summed ;; heuristic for next guess.
          r-firstmove ;; cached evaluation of a "blank" single board
          data/dict-answers ;; legal answers.  Can be customized.
          data/dict-allowed-guesses)) ;; legal guesses (contains the answers).  Can be customized.
          
     ;; examine-next-move produces output like:
     ;; 
     ;; {:best-guess "soare",
     ;;   :l-answer-lists (["aback" "abase" "abate" ...
     ;; ])}
     (examine-next-move ndle-state)
     
     ;; BEGIN LOOP

     ;; Here, make your guess and record the colors output from e.g. quordle.com.
     (def ndle-state
          (record-guess-results ndle-state
          "soare" ;; update this to your guess
     
          ;; Example return for ["spore", "deity", "manga", "snoop"]
          '(
            (2 1 0 2 2)
            (0 0 0 0 1)
            (0 0 1 0 0)
            (2 1 0 0 0)
            )))
     
     ;; Produces output like:
     ;; 
     ;; {:best-guess "clint",
     ;;   :l-answer-lists (["score" "shore" "snore"...
     ;;   "spore" "store" "swore"] ["beech" "beefy" ...
     ;; ])}
     (examine-next-move ndle-state)

     ;; END LOOP


### Usage: Running simulations

#### Single board (example: 4-dle)

    (harness/run-one-trial
      4
      r-firstmove
      ["spore", "deity", "manga", "snoop"]
      data/dict-allowed-guesses
      eval/select-best-guess-global-min
      "output.txt")

Screen output:

    == NEW GAME STATE ==
    {:round 1, :rounds-finished (), :found-words (), :guesses ("soare")}
    == NEW GAME STATE ==
    {:round 2,
     :rounds-finished (),
     :found-words (),
     :guesses ("soare" "punch")}

     ;; ....
     
    == NEW GAME STATE ==
    {:round 7,
     :rounds-finished (3 4 6 7),
     :found-words ("spore" "snoop" "deity" "manga"),
     :guesses ("soare" "punch" "spore" "snoop" "filmy" "deity" "manga")}


File output:

     spore,deity,manga,snoop;3,4,6,7;soare,punch,spore,snoop,filmy,deity,manga

`(run-many-trials)' is simply `(run-one-trial)' mapped over a list.  This is useful for creating a list (test set) and comparing different strategies on the same set.

#### Example:Run 10 random 2-dle trials
    (harness/run-random-trials
      10
      2
      r-firstmove
      data/dict-answers
      data/dict-allowed-guesses
      eval/select-best-guess-global-min
      "output.txt")

	 
File output example:

    spice,slide;4,6;soare,pling,yucko,spice,lemel,slide
    sorry,owner;2,5;soare,sorry,dated,naira,owner
    lemur,whose;3,6;soare,ethic,whose,duply,lemel,lemur
    ample,sloth;3,5;soare,cloot,sloth,aping,ample
    clank,ashen;3,5;soare,koses,ashen,pubco,clank
    abhor,shirt;3,4;soare,light,abhor,shirt
    flask,fiber;4,6;soare,chugs,iftar,flask,nixie,fiber
    amble,mirth;4,5;soare,glint,bayes,amble,mirth
    whiny,staid;4,5;soare,thilk,yucko,whiny,staid
    crazy,pulpy;4,5;soare,clint,lippy,crazy,pulpy
    ~                                               

### Usage: (Optional) Using pattern-matching to limit the dictionary.

Sometimes you may see friends' solutions *before* you solve,
which can give the enterprising Wordle shark extra information.

It's not uncommon to see solutions grids (0 = BLACK, 1 = YELLOW, 2 = GREEN) like 

    01210
    10202
    02222
    02222
    02222
    22222

Assuming they haven't repeated guesses, this means the answer has to be among a set of at least four words which share the last four letters.
You can use this to your advantage by pre-eliminating those ansers from the dictionary with:

	(def l-answers data/dict-answers) ;; or nest earlier uses of the below function.
	(count l-answers) ;; yields 2309
	(def l-answers (select-similar-block '(0 2 2 2 2) 4 l-answers))
	(count l-answers) ;; yields 2087

You can combine several word lists (sequneces of word strings) with

	(def l-answers (intersect-blocks
	     '("agave" "grape" "sugar")
	     '("grape" "green" "agave"))) ;; yields '("agave" "grape")

## Enchancements (TODOs)

### Notes for presenation

- lemel and sorting
- -100 entropy
- what to do with finished sub-puzzles
- tiebreaking
- vern's gambit
- pmap
- Clojure 1.8?  Should update?
- sometimes overruns nREPL memory limits
 
### Fragility to errors

If something's entered incorrectly, you will end up in the "I don't know, lemel" case quickly.

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

