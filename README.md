# chocolatier

A work-in-progress web game engine for repl driven game development written in ClojureScript using Pixi.js as a rendering engine.

## Usage

### With `figwheel`
The following instructions will start a browser connected repl and launch the demo game:

0. Clone the project and all submodules `git clone --recursive https://github.com/alexkehayias/chocolatier`
1. Start the browser REPL server `lein figwheel`
2. Navigate your browser to `http://127.0.0.1:3449` to connect to the REPL
3. In the REPL, change namespaces `(in-ns 'chocolatier.core)` then start the game `(restart-game!)`. This may take a few seconds to load assets and init WebGL (if your browser supports it).

### With `figwheel` and emacs using `cider`

The recommended setup is to connect to the `figwheel` REPL server from emacs `cider` so that you can integrate the text editor into the development workflow. This allows you to send code to the running repl for evaluation using emacs `cider-mode`.

After completing step 2 from the `figwheel` instructions above, in emacs:

1. Connect to the `figwheel` REPL `M-x cider-connect RET localhost RET 8999`
2. Start ClojureScript REPL `(do (use 'figwheel-sidecar.repl-api) (cljs-repl))`
3. Open the game file `C-x C-f RET /chocolatier/src/cljs/chocolatier/core.cljs`
4. Change to the namespace in that file `C-c M-n`
5. Evaluate the file `C-c M-k`
6. Start the demo game `(restart-game!)`

### Compiling with advanced optimizations

Currently not supported due to external library dependencies which will require an externs file or a rewrite of how external objects are called.

## Entity Component System

The game engine implemented using a modified entity component system which organizes aspects of a game modularly. Think about it less as a bunch of objects with their own state and methods and more like a database where you query for functionality, state, based on a certain aspect or entity.

Organization:

1. Scene - collection of system labels to be looked up and called by the game loop (main menu, random encounter, world map, etc)
2. System - functions that operates on a component (or not) and returns updated game state. Examples: input, rendering, collision detection
3. Components - hold state and component function relating to a certain aspect. Polymorphism can be used to dispatch on entity IDs (or however else you want) for finer control using multimethods. Examples: moveable, user controllable, collidable, destructable
4. Entities - unique IDs that have a list of components to they participate in. Examples: `{:player1 [:controllable :moveable :destructable]}`

### Example

The following example implements a simple game loop, system, component, and entities to show you how it all fits together. See the rest of the game for a more in-depth example (and graphics).

```clojure
(ns my-ns
  (:require [chocolatier.engine.ces :as ces])

(defn game-loop
  "Simple game loop that runs 10 times and returns the state after 10 frames."
  [state frame-count]
  (if (> frame-count 10)
    state
    (let [scene-id (get-in state [:game :scene-id])
          fns (ces/get-system-fns state (-> state :scenes scene-id))
          updated-state (ces/iter-fns state fns)]
      (recur updated-state scene-id (inc frame-count)))))

(defn test-system
  "Call all the component functions and return update game state"
  [state component-f entity-ids]
  (reduce component-f state entity-ids))

(defn test-component-fn
  "Increment the :x value by 1"
  [entity-id component-state inbox]
  (assoc component-state :x (inc (:x component-state))))

(defn my-game
  "Test the entire CES implementation with a system that changes component state"
  []
  (->
    (ces/mk-game-state {}
                       :test-scene
                       [:scene :test-scene [:test-system]]
                       [:system :test-system test-system :testable]
                       [:component :testable test-component-fn]
                       [:entity :player1 :components [[:testable {:x 0 :y 0}]]]
                       [:entity :player2 :components [[:testable {:x 10 :y 10]])
      (game-loop 0)))

;; This will run 10 times and return the final state
(my-game)

```

## State

The game is represented as a hashmap and a collection of functions that transform the state. This makes it easy to test game logic by calling functions with mocked data (since it's just a hasmap). You should be able to test any system, component, etc with data only.

## Browser Connected Repl (Brepl)

A browser repl is automatically available when the server is started when using `lein figwheel`. This allows you to dynamically re-evaluate the code running in the browser without a page refresh. Static files can also watched and reload the game when changed. See the `figwheel` documentation for more.

## Cross-component communication

A global pub-sub event queue is available for any component enabling cross component communication without coupling the state of any of the components. For example, suppose the render component needs to update the screen position of the player sprite. The render component needs information from the input component, but we don't want to couple the state of either components together. Instead of directly accessing the input component's state from the render component we subscribe to messages about player movement and update based on that. We can broadcast that information without any knowledge of who is listening to it and no one can change the component state from another component.

### Events

By default, component functions created with `ces/mk-component` can output a single value, representing component state, or two values, component state and a collection of events to emit.

For example, the following component will emit a single event called `:my-event` with the message `{:foo :bar}`:

```clojure
(defn component-a [entity-id component-state inbox]
  [component-state [(ev/mk-event {:foo :bar} [:my-event entity-id])]]])
```

### Subscriptions

Any component can subscribe to it by calling `events/subscribe` on the game state. For example, subscribing `:component-b` to the `:my-event` event:

```clojure
(subscribe my-game-state :my-event :component-b)
```

Note: this is an idempotent operation and you can not subscribe more than once.

The subscribed component will receive the event in it's inbox (third arg to component functions by default) as soon as the component function is called next. This allows for events to be sent and received within the same frame.

### Hierarchical Events

Events use selectors (a vector of keywords) to determine which events are returned. This allows specificity in messages returned. For example, an event emitted with selectors `:a :b :c` will be returned to all subscribers of `:b` and `:a`.

## Tilemaps

The game engine supports tilemaps generated from the Tiled map editor http://www.mapeditor.org. Export the map as json and include the tileset image in the `resources/public/static/images` directory.

Tilemaps require all assets to be loaded (tileset images) to prevent any race conditions with loading a tilemap see `chocolatier.engine.systems.tiles/load-assets`. Tilemaps are loaded asynchronously from the server via `chocolatier.engine.systems.tiles/load-tilemap` which takes a callback.

## Running Tests

Currently does not support `lein-cljsbuild` tests. Instead, load a namespace in a brepl and use the `test-ns` macro to run the tests.

## Performance

Performance tuning is an ongoing process and the project has not been thoroughly optimized. ClojureScript presents challenges for optimization including garbage collection, persistent data structures, and functional paradigms that js engines have difficulty optimizing.

Where appropriate, transient state should be used when operating on large collections and hashmaps for better performance. See `chocolatier.macros` in the `clj` source directory for helpers with transient state.

Next steps:

- Comprehensive benchmarking and profiling
- Introduce macros to rewrite systems/component/event functions to work better with js engines without sacrificing composability

## Benchmarks

Naive frames per second benchmarks are available at `chocolatier.engine.benchmarks` for measuring the performance of the framework.

## License

Copyright © 2014 Alex Kehayias

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
