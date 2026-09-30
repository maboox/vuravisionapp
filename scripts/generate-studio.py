import math,re,json
from pathlib import Path
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/src/main/assets/studio';OUT.mkdir(parents=True,exist_ok=True)
PRE=ROOT/'docs/screenshots';PRE.mkdir(exist_ok=True)
# Original vector illustrations. All fills are continuous, editable pen hatching.
class Art:
 def __init__(self,name):self.name=name;self.strokes=[];self.im=Image.new('RGB',(600,800),'#f4eee2');self.d=ImageDraw.Draw(self.im)
 def path(self,data,color='#302e38',width=2,fill=None):
  parts=re.split(r'(?=M )',data)
  parts=[p.strip() for p in parts if p.strip()]
  if len(parts)>1:
   for part in parts:self.path(part,color,width,fill)
   return
  tok=re.findall(r'[MLCQZ]|-?\d+(?:\.\d+)?',data);i=0;p=(0,0);start=p;pts=[]
  while i<len(tok):
   cmd=tok[i];i+=1
   if cmd=='Z':pts.append(start);p=start;continue
   n={'M':2,'L':2,'C':6,'Q':4}[cmd];v=list(map(float,tok[i:i+n]));i+=n
   if cmd=='M':p=tuple(v);start=p;pts.append(p)
   elif cmd=='L':p=tuple(v);pts.append(p)
   else:
    a=p;b=tuple(v[:2]);c=tuple(v[2:4]);end=tuple(v[-2:]);steps=max(8,int(sum(math.dist(x,y) for x,y in zip([a,b,c],[b,c,end]))/4))
    for k in range(1,steps+1):
     t=k/steps;u=1-t
     if cmd=='C':q=(u**3*a[0]+3*u*u*t*b[0]+3*u*t*t*c[0]+t**3*end[0],u**3*a[1]+3*u*u*t*b[1]+3*u*t*t*c[1]+t**3*end[1])
     else:q=(u*u*a[0]+2*u*t*b[0]+t*t*c[0],u*u*a[1]+2*u*t*b[1]+t*t*c[1])
     pts.append(q)
    p=end
  if fill:
   self.d.polygon(pts,fill=fill)
   lanes=[];previous=[]
   low=min(y for x,y in pts);high=max(y for x,y in pts)
   for step in range(math.ceil((high-low)/.75)):
    y=low+.4+step*.75
    if y>=high:break
    xs=[]
    for a,b in zip(pts,pts[1:]+pts[:1]):
     if (a[1]<=y<b[1]) or (b[1]<=y<a[1]):xs.append(a[0]+(y-a[1])*(b[0]-a[0])/(b[1]-a[1]))
    xs.sort();current=[];used=set()
    for j in range(0,len(xs)-1,2):
     left,right=xs[j:j+2]
     candidates=[(max(0,min(right,r)-max(left,l)),k,lane) for k,(l,r,lane) in enumerate(previous) if k not in used]
     overlap,k,lane=max(candidates,default=(0,-1,None),key=lambda c:c[0])
     if overlap<=0:lane=[];lanes.append(lane)
     else:used.add(k)
     pair=[(left,y),(right,y)]
     if len(lane)//2%2:pair.reverse()
     lane.extend(pair);current.append((left,right,lane))
    previous=current
   for lane in lanes:
    if lane:self.add(lane,fill,.9)
  if width>0:self.d.line(pts,fill=color,width=max(1,round(width)),joint='curve');self.add(pts,color,width)
 def add(self,pts,color,width):self.strokes.append({'color':color,'width':width,'points':[[round(x,2),round(y,2)] for x,y in pts]})
 def ellipse(self,x,y,rx,ry,fill,color=None,width=0):
  pts=[(x+rx*math.cos(t*math.pi/90),y+ry*math.sin(t*math.pi/90)) for t in range(181)]
  self.path('M '+' L '.join(f'{a:.2f} {b:.2f}' for a,b in pts)+' Z',color or fill,width,fill)
 def finish(self):
  (OUT/(self.name+'.json')).write_text(json.dumps({'width':600,'height':800,'strokes':self.strokes},separators=(',',':')))
  self.im.save(PRE/(self.name+'-v18.png'))
  print(self.name,len(self.strokes),sum(len(s['points']) for s in self.strokes))

a=Art('mona-vura')
a.path('M 20 20 L 580 20 L 580 780 L 20 780 Z','#b19461',3,'#e8dfca')
a.path('M 34 35 L 566 35 L 566 660 L 34 660 Z','#b19461',1,'#a9b2a1')
a.path('M 34 248 C 106 183 110 250 173 189 C 226 147 278 202 317 150 C 377 204 429 165 466 223 C 514 175 546 192 566 217 L 566 436 L 34 436 Z','#879b8a',0,'#879b8a')
a.path('M 34 347 C 120 255 196 308 279 263 C 378 340 449 258 566 302 L 566 454 L 34 454 Z','#697f70',0,'#697f70')
a.path('M 34 386 C 98 346 160 382 195 364 C 253 328 293 402 330 371 C 427 330 496 417 566 357 L 566 576 L 34 576 Z','#526b5e',0,'#526b5e')
for j in range(12):
 y=330+j*18;a.path(f'M 34 {y} C 98 {y-18} 115 {y+20} 164 {y+7}', '#b5c2ab',1)
a.path('M 54 700 C 55 554 101 488 176 456 C 194 393 183 373 181 322 C 138 221 150 110 230 80 C 283 43 355 67 383 91 C 455 118 466 226 435 320 C 409 379 433 422 424 466 C 512 508 553 590 552 700 Z','#2d3230',2,'#303b36')
a.path('M 204 356 C 223 334 372 331 390 360 L 410 474 C 351 523 252 520 184 472 Z','#714b36',1,'#ad8255')
a.path('M 229 340 C 249 396 353 392 376 332 L 375 425 C 338 462 274 455 239 413 Z','#d1ac70',0,'#cba569')
a.path('M 213 183 C 225 129 270 115 305 117 C 351 115 389 147 399 195 C 411 236 394 309 373 343 C 350 381 300 393 265 370 C 232 349 213 300 207 254 C 198 229 201 203 213 183 Z','#684b34',2,'#d9b87d')
a.path('M 307 121 C 358 122 392 159 399 200 C 409 248 392 311 374 339 C 375 293 349 291 356 247 C 361 204 329 164 307 121 Z','#c4a16a',0,'#c4a16a')
a.path('M 225 218 C 242 207 265 207 281 216','#574832',2.3)
a.path('M 326 215 C 343 204 365 205 379 216','#574832',2.3)
a.path('M 224 232 C 240 220 265 224 278 231 C 262 237 238 239 224 232 Z','#514d3c',1.6,'#e4cf9b')
a.path('M 328 231 C 343 220 367 222 379 230 C 364 237 340 239 328 231 Z','#514d3c',1.6,'#e4cf9b')
a.ellipse(252,231,4.6,5.6,'#514632');a.ellipse(350,230,4.6,5.6,'#514632')
a.path('M 299 234 C 296 257 286 282 294 292 C 302 296 317 295 323 290','#a28151',1.5)
a.path('M 281 288 C 286 287 290 290 293 291 M 318 291 C 323 286 329 289 331 292','#866547',1.3)
a.path('M 269 323 C 288 325 304 311 318 318 C 329 321 337 320 347 318 C 329 334 290 338 269 323 Z','#896049',1.4,'#b38763')
a.path('M 274 323 C 300 327 323 326 343 320','#6b5541',1.8)
a.path('M 281 340 C 300 349 321 346 333 339','#b99a63',1.5)
a.path('M 197 266 C 183 208 200 142 251 114 C 243 118 223 157 221 182 C 204 236 216 308 235 344 L 210 392 C 197 343 185 312 197 266 Z','#332e28',1,'#44392c')
a.path('M 309 112 C 355 101 398 143 414 188 C 433 259 398 322 387 363 C 391 408 421 438 427 474 L 389 453 C 365 380 405 292 399 220 C 393 175 357 133 309 112 Z','#332e28',1,'#44392c')
for j in range(14):
 a.path(f'M {196+j*3} {207+j*3} C {172+j*3} 291 {183+j*3} 384 {178+j*3} 451','#6f5b3e',.9)
 a.path(f'M {398+j*2} {196+j*3} C {436+j} 279 {386+j*2} 382 {431+j} 470','#756142',.9)
a.path('M 92 706 C 72 620 91 525 183 476 C 242 466 365 477 419 470 C 509 514 529 615 524 706 Z','#343730',2,'#3f493d')
a.path('M 180 465 C 239 508 352 511 419 466 L 416 494 C 344 540 254 535 175 494 Z','#252c28',1,'#252c28')
for j in range(9):a.path(f'M {131+j*15} {518+j*4} C {108+j*14} 612 {187+j*9} 650 {172+j*13} 707','#737457',1.3)
a.path('M 140 521 C 130 569 150 607 195 628 L 277 625 L 279 593 C 226 583 208 559 195 529 Z','#5f654d',1,'#656c51')
a.path('M 465 532 C 486 578 452 609 401 635 L 340 619 L 354 582 C 405 590 415 555 427 535 Z','#5f654d',1,'#656c51')
# Framed Vura emblem held in both hands.
a.path('M 187 551 L 413 551 L 413 716 L 187 716 Z','#422f22',3,'#ba9150')
a.path('M 199 563 L 401 563 L 401 704 L 199 704 Z','#ddc18b',2,'#202938')
a.path('M 248 584 L 257 584 L 294 682 L 285 682 Z','#faa329',0,'#faa329')
a.path('M 257 584 L 274 584 L 311 682 L 294 682 Z','#f8f9fc',0,'#f8f9fc')
a.path('M 344 584 L 352 584 L 326 644 L 318 644 Z','#f8f9fc',0,'#f8f9fc')
a.path('M 176 579 C 170 576 169 593 182 606 C 192 616 214 623 228 617 C 235 613 223 609 205 604 C 230 612 237 608 231 603 C 226 598 213 596 203 590 C 227 599 233 593 224 588 C 211 580 196 582 191 574 C 188 566 180 567 182 582 Z','#87684b',1.4,'#d8b57b')
a.path('M 424 590 C 430 585 433 596 420 609 C 406 622 384 628 373 620 C 368 616 384 613 401 606 C 377 615 371 609 378 604 C 385 599 397 598 407 591 C 384 598 378 593 387 588 C 399 581 414 582 418 577 C 424 570 430 575 424 590 Z','#87684b',1.4,'#d8b57b')
a.path('M 37 739 L 563 739','#b19461',1)
a.finish()

b=Art('aurora')
b.path('M 20 20 L 580 20 L 580 780 L 20 780 Z','#b5d4d4',2,'#e8f4f2')
b.ellipse(302,329,246,258,'#213247');b.ellipse(302,329,227,239,'#2d4d61')
# Orbit and constellations.
for j in range(12):
 t=j*math.pi/6;x=302+230*math.cos(t);y=329+245*math.sin(t)
 b.ellipse(x,y,2,2,'#e8c781')
b.path('M 48 286 C 160 143 447 156 550 292 M 65 421 C 192 541 437 508 548 367','#bdccbf',1)
for x,y in [(90,138),(488,148),(532,491),(70,501),(449,65)]:
 b.path(f'M {x-9} {y} L {x+9} {y} M {x} {y-9} L {x} {y+9}', '#c9a870',1.5)
b.path('M 96 695 C 94 578 144 519 212 482 L 389 482 C 474 526 510 593 509 695 Z','#172733',2,'#182c3b')
b.path('M 182 159 C 208 83 320 68 389 112 C 479 153 481 281 449 377 C 434 425 454 490 482 547 C 382 590 217 563 123 534 C 159 467 142 414 145 344 C 126 257 139 201 182 159 Z','#142834',2,'#225968')
b.path('M 195 193 C 204 153 267 131 317 142 C 359 146 399 181 407 223 L 396 323 C 380 378 340 406 306 406 C 262 406 219 370 204 322 Z','#675e69',1.8,'#ffe0c2')
b.path('M 316 142 C 372 148 401 182 407 228 L 396 323 C 384 356 364 379 341 393 C 375 344 359 328 364 294 C 370 243 339 187 316 142 Z','#f2bfad',0,'#f2bfad')
b.path('M 265 392 L 264 465 C 284 490 327 489 347 462 L 344 392 C 318 412 291 412 265 392 Z','#675e69',1.5,'#ffdcc0')
b.path('M 267 399 C 288 430 325 439 344 407 L 343 428 C 312 451 288 442 267 427 Z','#e9ad9f',0,'#e9ad9f')
# Eyes, lashes and iris highlights.
for off in [0,104]:
 b.path(f'M {219+off} 267 C {233+off} 249 {262+off} 252 {281+off} 272 C {264+off} 294 {235+off} 291 {219+off} 267 Z','#203340',2,'#fffaf0')
 b.ellipse(250+off,272,14,17,'#40b1b2','#243e4b',1.6)
 b.ellipse(250+off,273,8,11,'#203747')
 b.ellipse(245+off,265,4,4,'#ffffff');b.ellipse(255+off,279,2,2,'#e6ddaa')
 b.path(f'M {215+off} 262 C {233+off} 245 {259+off} 247 {281+off} 268 M {219+off} 260 L {213+off} 250 M {227+off} 255 L {223+off} 244','#1b2b37',3)
 b.path(f'M {220+off} 235 C {238+off} 225 {262+off} 225 {278+off} 237','#35474d',2.3)
b.path('M 307 277 C 302 296 298 310 306 314 L 314 314','#d59e91',1.5)
b.path('M 283 349 C 297 342 312 343 329 346 C 316 357 298 359 283 349 Z','#b16d79',1.2,'#e9a5a1')
b.path('M 284 349 C 300 350 315 349 328 346','#875966',1.3)
b.path('M 236 312 L 260 318 M 231 318 L 257 323 M 356 319 L 380 313 M 360 326 L 383 318','#e9ab9d',1.6)
# Flowing hair, deliberate angular anime fringe.
b.path('M 184 185 C 205 95 316 88 377 128 C 414 154 426 203 419 255 C 386 242 365 214 348 173 C 352 214 328 253 306 265 C 315 219 306 181 288 160 C 281 209 250 244 211 261 C 229 221 228 192 228 174 C 196 222 192 286 185 327 L 154 346 C 139 260 150 213 184 185 Z','#152e3e',2,'#286675')
b.path('M 288 145 C 310 160 329 195 319 238 C 347 215 352 190 339 163 C 354 189 373 214 397 228 C 390 172 352 142 288 145 Z','#488e93',0,'#488e93')
b.path('M 184 217 C 167 302 203 378 199 435 C 192 476 166 508 151 542 C 207 523 239 491 238 442 C 237 410 210 378 205 342 Z','#183541',2,'#337b84')
b.path('M 417 237 C 409 320 433 375 423 431 C 419 471 439 503 469 543 C 411 524 381 497 380 456 C 379 418 402 386 397 348 Z','#183541',2,'#337b84')
for j in range(10):
 b.path(f'M {162+j*4} {248+j*3} C {171+j*4} 330 {217+j*2} 433 {167+j*6} 515','#69b5b4',1)
 b.path(f'M {416+j*2} {269+j*2} C {402+j*3} 358 {402+j*2} 438 {449+j*2} 517','#69b5b4',1)
# Star ornament, earrings, cape trim.
b.path('M 178 212 L 189 227 L 207 223 L 197 239 L 204 256 L 187 249 L 173 260 L 175 241 L 160 230 L 179 230 Z','#987d51',1.2,'#eac980')
b.ellipse(206,334,4,5,'#d5b879');b.path('M 206 340 L 206 374','#e6c47e',2);b.ellipse(206,377,5,8,'#3eb0b4','#e6c47e',1.5)
b.ellipse(397,338,4,5,'#d5b879');b.path('M 397 342 L 397 377','#e6c47e',2);b.ellipse(397,380,5,8,'#3eb0b4','#e6c47e',1.5)
b.path('M 218 477 L 267 468 L 305 503 L 346 468 L 392 483 L 333 554 L 278 554 Z','#c7a66e',2,'#f4e5c4')
b.path('M 207 494 L 266 542 L 248 588 L 201 560 L 164 623 M 401 494 L 344 542 L 362 588 L 412 560 L 449 623','#e3c783',2)
b.path('M 267 539 L 307 579 L 348 539 L 335 608 L 306 629 L 280 608 Z','#d4b578',1.6,'#2d6e7a')
b.ellipse(307,568,10,14,'#61c5bf','#e4c481',2)
for j in range(6):b.path(f'M {116+j*12} {613-j*5} C {146+j*17} 650 {206+j*10} 674 {235+j*8} 694','#355365',1.6)
for j in range(6):b.path(f'M {492-j*12} {613-j*5} C {464-j*17} 650 {407-j*10} 674 {378-j*8} 694','#355365',1.6)
b.path('M 37 730 L 563 730','#9fbdba',1)
b.finish()
