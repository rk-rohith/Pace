import json, os
HERE=os.path.dirname(os.path.abspath(__file__))
os.chdir(HERE)
exec(open('plan.py').read().split("import json")[0]); exec(open('reels.py').read())
from recipes_text import T
from reels_text import T_REEL
from grocery import GRO_A, GRO_B
T.update(T_REEL)
T['tandoori']=(T['tandoori'][0],T['tandoori'][1],T['tandoori'][2],"Handbook p.13. Your reel's version adds 1 tsp soy sauce and garlic powder to the marinade and scores the chicken deeply; pan-fry 6 min a side or air-fry at 195 °C, 8 min a side.")
T['banana']=('Basics',['1 medium banana','Black coffee (no sugar)'],['Eat 30–45 min before training.'],'Skip it on rest days, or if you train fasted.')
T['shake']=('Basics',['1 scoop of your whey (38.5 g)','250 ml water'],['Shake and drink within an hour after training.'],'Milk instead of water adds about 90 kcal per 200 ml.')
T['buttermilk']=('Basics',['75 g low-fat curd','200 ml water','Salt, roasted cumin, curry leaves'],['Whisk everything together.'],'')
# Grocery ingredients: key -> (shopping-list name, section, unit). Unit g/ml/pc/scoop.
INGREDIENTS = {
 'chicken':('Chicken breast, boneless','Meat, fish & eggs','g'), 'fish':('Fish fillets (seer, basa, rohu)','Meat, fish & eggs','g'),
 'prawn':('Prawns, cleaned','Meat, fish & eggs','g'), 'egg_pc':('Eggs','Meat, fish & eggs','pc'), 'eggwhite_pc':('Eggs','Meat, fish & eggs','pc'),
 'paneer_lf':('Low-fat paneer','Dairy','g'), 'paneer':('Paneer','Dairy','g'), 'curd_lf':('Curd (dahi)','Dairy','g'),
 'greek':('Greek yogurt (or hang double the curd)','Dairy','g'), 'milk_dt':('Double-toned milk','Dairy','ml'),
 'mozzarella':('Mozzarella','Dairy','g'), 'butter':('Butter','Dairy','g'), 'whey_scoop':('Whey protein','Dairy','scoop'),
 'onion':('Onion','Vegetables','g'), 'tomato':('Tomato','Vegetables','g'), 'veg_mix':('Mixed vegetables (beans, carrot, broccoli, cabbage)','Vegetables','g'),
 'sambar_veg':('Sambar vegetables (drumstick, brinjal, pumpkin, okra)','Vegetables','g'), 'spinach':('Spinach','Vegetables','g'),
 'capsicum':('Capsicum','Vegetables','g'), 'cucumber':('Cucumber','Vegetables','g'), 'peas':('Green peas (frozen ok)','Vegetables','g'),
 'okra':('Okra','Vegetables','g'), 'cabbage':('Cabbage','Vegetables','g'), 'herbs':('Coriander & mint','Vegetables','g'), 'garlic':('Garlic','Vegetables','g'),
 'banana_pc':('Bananas','Fruit','pc'), 'apple_pc':('Apples','Fruit','pc'), 'guava_pc':('Guava','Fruit','pc'), 'papaya':('Papaya','Fruit','g'), 'pomegranate':('Pomegranate arils','Fruit','g'),
 'rice_raw':('Rice','Grains, dals & snacks','g'), 'basmati_raw':('Basmati rice','Grains, dals & snacks','g'), 'atta':('Whole wheat atta','Grains, dals & snacks','g'),
 'oats':('Rolled oats','Grains, dals & snacks','g'), 'idli_batter':('Idli / dosa batter','Grains, dals & snacks','g'), 'pasta':('Macaroni','Grains, dals & snacks','g'),
 'toor':('Toor dal','Grains, dals & snacks','g'), 'moong_whole':('Whole green moong','Grains, dals & snacks','g'), 'moong_yellow':('Yellow moong dal','Grains, dals & snacks','g'),
 'soya':('Soya chunks / granules','Grains, dals & snacks','g'), 'kala_chana':('Kala chana','Grains, dals & snacks','g'), 'roasted_chana':('Roasted chana','Grains, dals & snacks','g'),
 'makhana':('Makhana','Grains, dals & snacks','g'), 'cashew':('Cashews','Grains, dals & snacks','g'), 'cornflour':('Cornflour','Grains, dals & snacks','g'),
 'ricefl':('Rice flour','Grains, dals & snacks','g'), 'sesame':('Sesame seeds','Grains, dals & snacks','g'),
 'oil':('Cooking oil','Pantry','ml'), 'ghee':('Ghee','Pantry','g'), 'tamarind':('Tamarind','Pantry','g'),
 'gochujang':('Gochujang (or schezwan sauce)','Pantry','g'), 'soysauce':('Soy sauce','Pantry','ml'),
}
STAPLES=[('Ginger','100 g',''),('Green chillies','50 g',''),('Curry leaves','1 bunch',''),('Lemons','3–4',''),
 ('Spices','check stock','turmeric, chilli, Kashmiri chilli, coriander & cumin powder, cumin & mustard seeds, garam masala, sambar powder, tandoori/tikka masala, biryani & kabab masala, black pepper, fennel, fenugreek, hing, kasuri methi, chaat masala')]
recipes=[]
for k,(cat,ing,steps,tip) in T.items():
    r=R[k]; m=r['m']
    src = r['src'] if r['src'].startswith('Handbook') else ('From your reel' if k in REEL else ('Basic' if cat=='Basics' else 'Added for your plan'))
    urls=[REEL[k]] if k in REEL else []
    if k=='tandoori': urls=[EXTRA_REEL['tandoori']]
    if k=='greensoya': urls.append(EXTRA_REEL['greensoya2'])
    items={n:round(q,2) for n,q in r['ings'].items() if n in INGREDIENTS}
    recipes.append(dict(id=k,name=r['name'],category=cat,source=src,veg=r['veg'],kcal=round(m[0]),protein=round(m[1]),carbs=round(m[2]),fat=round(m[3]),ingredients=ing,steps=steps,tip=tip,reels=urls,items=items))
SLOTS_T=[('6:00','Pre-workout','SNACK'),('7:30','Post-workout','SNACK'),('8:30','Breakfast','BREAKFAST'),('13:30','Lunch','LUNCH'),('17:00','Snack','SNACK'),('20:30','Dinner','DINNER')]
SLOTS_R=[('8:30','Breakfast','BREAKFAST'),('13:30','Lunch','LUNCH'),('17:00','Snack','SNACK'),('20:30','Dinner','DINNER')]
def week(plan):
    days=[]
    for day,kind,veg,items in plan:
        slots=SLOTS_T if len(items)==6 else SLOTS_R
        days.append(dict(day=day,kind=kind,veg=veg,meals=[dict(time=t,label=l,mealType=mt,recipes=list(it) if isinstance(it,tuple) else [it]) for (t,l,mt),it in zip(slots,items)]))
    return days
def gro(G): return [dict(section=s,items=[dict(name=n,qty=q,note=no) for n,q,no in items]) for s,items in G]
PREP=[
 'Portion the chicken into 150 g bags (ask for one portion minced in Week A). Fridge Mon–Wed, freeze the rest.',
 'Week A: portion the fish into 3 × 150 g; freeze Friday and Sunday bags.',
 'Pressure-cook 90 g toor dal and make 3 bowls of protein sambar.',
 'Make an onion-tomato masala base for the week\'s curries. Keeps 5 days.',
 'Grind ginger-garlic paste.',
 'Wash and chop the mixed vegetables into 250 g bags.',
 'Hang 400–600 g curd overnight in a cloth for Greek-style curd.',
 'Soak green moong on Saturday night for Monday\'s sprouts; soak more the night before each pesarattu day.',
 'Week B: grind the egg ghee roast masala and freeze half.',
 'Marinate tandoori chicken the night before Friday.',
]
ADJ=[('+150 kcal','Add 1 chapati to dinner (120) or 100 g cooked rice (130).'),('+75 kcal','Add a glass of buttermilk and 15 g roasted chana.'),('−75 kcal','Skip the pre-workout banana, or halve the snack.'),('−150 kcal','Swap dinner rice for extra vegetables (−130) and use 3 g oil instead of 5 g.')]
data=dict(version=2,recipes=recipes,ingredients={k:dict(name=n,section=sec,unit=u) for k,(n,sec,u) in INGREDIENTS.items()},staples=[dict(name=n,qty=q,note=no) for n,q,no in STAPLES],weeks=[dict(id='A',title='Week A',subtitle='Handbook',days=week(PLAN)),dict(id='B',title='Week B',subtitle='Your reels',days=week(PLAN_B))],
 groceries=dict(A=gro(GRO_A),B=gro(GRO_B)),prep=PREP,adjustments=[dict(change=a,how=b) for a,b in ADJ])
import os
OUT=os.path.join(HERE,'..','..','app','src','main','assets','meal_plan.json')
json.dump(data,open(OUT,'w'),ensure_ascii=False,indent=1)
print(len(recipes),'recipes; ids ok:', all(rid in T for w in data['weeks'] for d in w['days'] for m in d['meals'] for rid in m['recipes']))
