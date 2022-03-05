# wordle-solver

This is a REPL-based assistant to solving "easy mode" Wordle, circa early 2022.


## Installation

Download from http://example.com/FIXME.

## Usage: Startup

Edit dictionary of allowed guesses at wordle-allowed-guesses.txt.
Edit dictionary of possible answers at wordle-answers.txt

    $ lein repl

## Usage: Loading the dictionaries

Edit dictionary of allowed guesses at wordle-allowed-guesses.txt.
Edit dictionary of possible answers at wordle-answers.txt

    $ lein repl


## Usage: (Optional) Using pattern-matching to limit the dictionary.

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

## Usage: Generating the next best guess

Entropy is used in a similar way to Alex Healy's treatment here: http://www.alexhealy.net/papers/wordle.pdf.

	$ ;; DO ONCE ON INIT
	$	(def l-answers dict-answers)
	$	(def l-allowed-guesses dict-allowed-guesses)
	$	(def r-top   (evaluate-all-moves l-answers l-allowed-guesses)) ;; first run takes 10-15 minutes.
	$	(def r-evals r-top)

	$$ ;; FOR ANY GIVEN STEP {
  	%% (evaluate-all-moves l-answers l-allowed-guesses)
    $$ (def r-evals *1)
  	$$ (pprint (take 10 (just-words-and-entropy r-evals)))

 	$$ (pprint (take 10 (viable-answer-words l-answers r-evals)))

  	$$ ;; MAKE YOUR CHOICE
	$$ (def w-word "cleat")
	$$ (def response-mask '(0 0 2 2 0))

	$$ (play-move l-answers l-allowed-guesses r-evals w-word response-mask)
	$$ (def l-answers *1)

	$$ }  BACK TO NEXT STEP
### Bugs


#### Major: Answers duplicated across response buckets.

Example: DACHA vs. HAIRY
;0 2 0 1 1 = [^DC]A[^DC][^HDC] includes A, H
(correct one) -> 0 2 0 1 0 = [^DCA]A[^DCA][^HDCA][^DCA] includes H
but hairy will show up in both sets as "matching" 


#### Enhancement: Build hard mode in

The difference between hard mode and easy mode is exactly what should go in "l-allowed-guesses".
In easy mode, l-allowed-guesses never changes - it's always precisely the loaded allowed guess dictionary.
In hard mode, l-allowed-guesses needs to respect the yellow and green (but can ignore the black) elements of the previous response, and therefore, the intersection of all previous responses.  

It is possible to cull the l-allowed-guesses list to fir this.  It hasn't been implemented.

#### Others

- Filter empty results in evaluation output for readbility
- Bug: An answer set with one option should make everything -1 (b/c you know the answer!)
