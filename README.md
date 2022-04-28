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


#### (FIXED) Major: Answers duplicated across response buckets.

Example: DACHA vs. correct answer HAIRY
;0 2 0 1 1 = [^DC]A[^DC][^HDC] includes A, H
(correct one) -> 0 2 0 1 0 = [^DCA]A[^DCA][^HDCA][^DCA] includes H
but hairy will show up in both sets as "matching" 

Example: Guess ASSES vs. answer MASSE SHOULD yield 1 1 2 1 0, but it NOW yields 1 1 2 1 1
1: Y - There's an A, and it's not here.
2: Y- There's a not green S somewhere else in the puzzle
3: G- This is an actual match
4: Y - There's a not green E somewhere else in the puzzle
5: B - There's no S in this spot, and there is no spot for this S.

We can state: The number of S's in the answer is the number of greens plus yellows.  
So the rule is: Grep for (has exactly Y+G S's)

ASSES could also be scored against answer MASSE in two ways, then.
10211 - the last S gets credit
11210 - the first S gets credit - correct answer

The yellows will distribute left-to-right, so the alphabetically LAST answer is the correct one for any duplicates.


What is the actual rule?
- A square is green if that slot has that letter.
- A square is yellow if, removing all greens, that 

====
(FIXED) BUG 2: If the answer is PASSE, then DACHA should be 0 2 0 0 0, not that AND 0 2 0 0 1 (count of A yellows+greens should be 1, not 2)
(FIXED) Bug 3: If the answer is BLAND, then DACHA should be 1 1 0 0 0, not 1 1 0 0 1 (by count)
(FIXED) Bug 3a: TODO why isn't it generating 1 0 0 0 1 as well? 

FOR EACH LETTER, createa  (G, Y, G)  (B), (Y B) (B Y)... list when seen.

FIX 1: If spot is green, put letter in that spot in regex, add +1 to total count for that letter.
FIX 2: If spot is yellow, filter letter out of that spot in regex, add + 1 to the total count needed for that letter.
FIX 3: If spot is black, filter letter out of that spot in the regex.   If there are no previous [or, by rule 4, any] yellow spots with that letter for it, throw in regex as exclude from every space.  Otherwise, can't.
FIX 4: If spot is yellow and there's a previous black for that letter, return totally empty filter.
RESULT: 
- Get empty flag, regex, letter -> count
- If empty, return negative
- Else generate letter filters, regex function, and compose all of these (order doesn't matter)

This is the path:
- Compute regex for all: green is in, yellow or black is out for that space.  That's the regex
- Compute total count for all.  If there's a black, it's equal.  If not, it's at least that many.  Those are the other functions.
- Ensure no black precedes a yellow.  If so, make a null.
- first step : get to
{\a: (BLACK YELLOW) \b: (GREEN) }etc.
 (update (update {} \a conj 1) \a conj 2)

EXTRA FIX: remove the kludge / kluge throw in earlier.

#### Enhancement: Build hard mode in

The difference between hard mode and easy mode is exactly what should go in "l-allowed-guesses".
In easy mode, l-allowed-guesses never changes - it's always precisely the loaded allowed guess dictionary.
In hard mode, l-allowed-guesses needs to respect the yellow and green (but can ignore the black) elements of the previous response, and therefore, the intersection of all previous responses.  

It is possible to cull the l-allowed-guesses list to fir this.  It hasn't been implemented.

#### Others

- Filter empty results in evaluation output for readbility
- Bug: An answer set with one option should make everything -1 (b/c you know the answer!)
