import time
import math
import random

# --- Mock Constants & Globals ---
WEIGHT_THRESHOLD = 0.5
STABLE_DELTA_THRESHOLD = 0.1
STABLE_TIME = 1000        # ms
COOLDOWN_TIME = 3000      # ms

# States
WAITING = 0
DETECTING = 1
COOLDOWN = 2

# Global State
current_state = WAITING
last_weight = 0.0
base_weight = 0.0
last_change_time = 0
last_send_time = 0

# Mock Hardware
current_time_ms = 0
current_scale_weight = 0.0
tare_offset = 0.0

def millis():
    return current_time_ms

def get_weight_units():
    return current_scale_weight - tare_offset

def scale_tare():
    global tare_offset
    tare_offset = current_scale_weight
    print(f"    [HARDWARE] Scale Tared. (Raw: {current_scale_weight:.2f}, Output: 0.00)")

# --- Logic Functions (Mirrors C++) ---
def return_amount(delta):
    if 0.8 <= delta < 2.5: return 1
    if 4.0 <= delta < 6.0: return 100
    if 6.0 <= delta < 9.0: return 500
    return 0
    
def send_firestore(amount):
    print(f"    [NETWORK] Sending {amount} Yen to Firestore... SUCCESS")
    return True

def loop():
    global current_state, last_weight, base_weight, last_change_time, last_send_time
    
    now = millis()
    weight = get_weight_units()
    
    if current_state == WAITING:
        if abs(weight - last_weight) > WEIGHT_THRESHOLD:
            current_state = DETECTING
            last_change_time = now
            base_weight = last_weight
            print(f"  -> State Changed: DETECTING (Base: {base_weight:.2f}, Curr: {weight:.2f})")
        last_weight = weight

    elif current_state == DETECTING:
        if abs(weight - last_weight) > STABLE_DELTA_THRESHOLD:
            last_change_time = now
        last_weight = weight
        
        if now - last_change_time > STABLE_TIME:
            print(f"    [DETECT] System Stable. Weight: {weight:.2f}g")
            delta = weight - base_weight
            print(f"    [DETECT] Delta: {delta:.2f}g")
            
            amount = return_amount(delta)
            if amount > 0:
                print(f"    [LOGIC] Detected {amount} Yen!")
                send_firestore(amount)
                current_state = COOLDOWN
                last_send_time = now
                scale_tare()
                last_weight = 0.0
                base_weight = 0.0
                print(f"  -> State Changed: COOLDOWN")
            else:
                print(f"    [LOGIC] Detection Failed (Unknown Amount)")
                current_state = WAITING
                scale_tare()
                last_weight = 0.0
                base_weight = 0.0
                print(f"  -> State Changed: WAITING")

    elif current_state == COOLDOWN:
        if now - last_send_time > COOLDOWN_TIME:
            print(f"    [COOLDOWN] Cooldown Finished.")
            current_state = WAITING
            scale_tare()
            last_weight = 0.0
            base_weight = 0.0
            print(f"  -> State Changed: WAITING")

# --- Test Runner ---
def run_test_scenario(scenario_name, actions):
    global current_time_ms, current_scale_weight, tare_offset, current_state
    
    print(f"\n=== TEST: {scenario_name} ===")
    
    # Reset System
    current_state = WAITING
    tare_offset = 0.0
    current_scale_weight = 0.0
    current_time_ms = 0
    scale_tare() # Initial tare
    
    # Run loop for some time
    max_time = 15000 # 15 seconds simulation per test
    step = 100 # 100ms per loop
    
    action_idx = 0
    
    while current_time_ms < max_time:
        # Check if we need to perform an action
        if action_idx < len(actions) and current_time_ms >= actions[action_idx][0]:
            target_weight = actions[action_idx][1]
            desc = actions[action_idx][2]
            current_scale_weight = target_weight
            print(f"  [ACTION @ {current_time_ms}ms] {desc} (Raw Weight: {current_scale_weight:.2f}g)")
            action_idx += 1
            
        loop()
        current_time_ms += step

if __name__ == "__main__":
    print("=== STARTING 5-ITERATION ROBUSTNESS TEST ===")
    
    for i in range(1, 6):
        print(f"\n\n>>> ITERATION {i} <<<")
        
        # Simulate noise (-0.15g to +0.15g)
        noise = random.uniform(-0.15, 0.15)
        
        # 100 Yen (4.8g) + noise
        w100 = 4.8 + noise
        
        # 500 Yen (7.0g) + noise
        w500 = 7.0 + noise
        
        # Build scenario
        scenario = [
            (1000, w100, f"Place 100 Yen (Target: 4.8g, Actual: {w100:.2f}g)"),
            (5000, w500, f"Place 500 Yen (Target: 7.0g, Actual: {w500:.2f}g) after cooldown")
        ]
        
        run_test_scenario(f"Robustness Test #{i}", scenario)
        
        # Reset time for next loop
        current_time_ms = 0
