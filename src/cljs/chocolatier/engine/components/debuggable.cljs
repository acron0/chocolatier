(ns chocolatier.engine.components.debuggable
  (:require [chocolatier.utils.logging :as log]
            [chocolatier.engine.ces :as ces]
            [chocolatier.engine.pixi :as pixi]))

(defn include-renderable-state-and-stage
  "Include the renderable component state and stage (for drawing new things adhoc)
   in the args passed to draw-collision-zone"
  [state component-id entity-id]
  (let [renderable-state (ces/get-component-state state :renderable entity-id)
        component-state (ces/get-component-state state component-id entity-id)
        inbox (ces/get-event-inbox state component-id entity-id)
        stage (-> state :game :rendering-engine :stage)]
    [stage component-state renderable-state component-id entity-id inbox]))

(defn base-style!
  "Applies styles to the graphic."
  [graphic]
  (-> graphic
      (pixi/line-style 0)
      (pixi/fill 0xFFFF0B 0.3)))

(defn collision-style!
  [graphic]
  (-> graphic
      (pixi/line-style 0)
      (pixi/fill 0xFF0000 0.3)))

(defmulti draw-collision-zone
  "Debug collision detection by drawing circles for the hitzone and turning red 
   when a collision message is received"
  (fn [stage component-state renderable-state component-id entity-id inbox]
    entity-id))

(defmethod draw-collision-zone :default
  [stage component-state renderable-state component-id entity-id inbox]
  (let [{:keys [pos-x pos-y hit-radius height width]} renderable-state
        ;; Center hitzone on middle of entity
        half-height (/ height 2) 
        half-width (/ width 2) 
        x (+ pos-x half-width)
        y (+ pos-y half-height)
        ;; Try to get the sprite for collision zone or create a new one
        graphic (or (:graphic component-state) (pixi/mk-graphic! stage))]
    ;; If there is a collision going on change set the color to red
    (if (empty? inbox)
      (-> graphic (pixi/clear) (base-style!) (pixi/circle x y hit-radius))
      (-> graphic (pixi/clear) (collision-style!) (pixi/circle x y hit-radius)))
    ;; If the sprite does not exist it will add it to component state
    (assoc component-state :graphic graphic)))